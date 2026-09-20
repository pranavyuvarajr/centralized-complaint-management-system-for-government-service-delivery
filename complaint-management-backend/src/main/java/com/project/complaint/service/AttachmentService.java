package com.project.complaint.service;

import com.project.complaint.dto.AttachmentDto;
import com.project.complaint.entity.AttachmentKind;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintAttachment;
import com.project.complaint.entity.User;
import com.project.complaint.repository.ComplaintAttachmentRepository;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final ComplaintAttachmentRepository attachmentRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    private void checkAccess(Complaint complaint, User user, String role) {
        if (role.equals("CITIZEN") && !complaint.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have access to this complaint");
        }
        if (role.equals("OFFICIAL")) {
            if (complaint.getDepartment() == null || user.getDepartment() == null
                    || !complaint.getDepartment().getId().equals(user.getDepartment().getId())) {
                throw new RuntimeException("You do not have access to this complaint");
            }
        }
    }

    @Transactional
    public AttachmentDto.Response upload(Long complaintId, String email, String role, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file provided");
        if (file.getSize() > MAX_SIZE) throw new RuntimeException("File exceeds the 5MB size limit");
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new RuntimeException("Only JPG, PNG, and PDF files are allowed");
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!complaint.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You can only attach files to your own complaints");
        }

        ComplaintAttachment attachment = storeAttachment(complaint, file, AttachmentKind.EVIDENCE, user);
        return mapToResponse(attachment);
    }

    /**
     * Checks a JPG/PNG photo (present, at most 5MB, right type). Callers use it
     * to reject a bad photo BEFORE changing any complaint state.
     */
    public void validatePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RuntimeException("No photo provided");
        if (file.getSize() > MAX_SIZE) throw new RuntimeException("Photo exceeds the 5MB size limit");
        String type = file.getContentType();
        if (type == null || !(type.equals("image/jpeg") || type.equals("image/png"))) {
            throw new RuntimeException("Only JPG or PNG photos are allowed");
        }
    }

    /**
     * Stores a photo added by someone other than the complaint's citizen (the
     * completion photo an official/admin uploads when resolving) or a photo
     * the citizen adds when reopening. The caller has already authorised the
     * action; this is not exposed as an endpoint on its own. Runs inside the
     * caller's transaction, so if the surrounding change fails the record is
     * rolled back and the stored file is removed.
     */
    @Transactional
    public void addPhoto(Complaint complaint, User uploader, AttachmentKind kind, MultipartFile photo) {
        validatePhoto(photo);
        storeAttachment(complaint, photo, kind, uploader);
    }

    private ComplaintAttachment storeAttachment(Complaint complaint, MultipartFile file,
                                                AttachmentKind kind, User uploader) {
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String rawName = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();
            String originalName = Path.of(rawName).getFileName().toString();
            String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
            String storedName = UUID.randomUUID() + extension;
            Files.copy(file.getInputStream(), dir.resolve(storedName));
            removeFileIfTransactionFails(storedName);

            return attachmentRepository.save(ComplaintAttachment.builder()
                    .complaint(complaint)
                    .originalFileName(originalName)
                    .storedFileName(storedName)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .kind(kind)
                    .uploadedBy(uploader)
                    .build());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage());
        }
    }

    /** If the surrounding transaction doesn't commit, delete the file that was just written so it isn't orphaned. */
    private void removeFileIfTransactionFails(String storedName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) return;
                try {
                    Files.deleteIfExists(Paths.get(uploadDir).resolve(Path.of(storedName).getFileName()));
                } catch (IOException ignored) {
                    // Best effort: a leftover file is harmless.
                }
            }
        });
    }

    public List<AttachmentDto.Response> list(Long complaintId, String email, String role) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        checkAccess(complaint, user, role);

        return attachmentRepository.findByComplaintIdOrderByUploadedAtAsc(complaintId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ComplaintAttachment loadForDownload(Long attachmentId, String email, String role) {
        ComplaintAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        checkAccess(attachment.getComplaint(), user, role);
        return attachment;
    }

    public Resource loadAsResource(ComplaintAttachment attachment) {
        try {
            Path path = Paths.get(uploadDir).resolve(attachment.getStoredFileName());
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) throw new RuntimeException("File not found on server");
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid file path");
        }
    }

    /**
     * Removes every attachment record for a complaint and, once the
     * surrounding transaction commits, the stored files as well. Deleting
     * files only after a successful commit means a rolled-back complaint
     * delete never leaves database rows pointing at files that are already gone.
     */
    @Transactional
    public void deleteAllForComplaint(Long complaintId) {
        List<ComplaintAttachment> attachments = attachmentRepository.findByComplaintIdOrderByUploadedAtAsc(complaintId);
        if (attachments.isEmpty()) return;

        List<String> storedNames = attachments.stream()
                .map(ComplaintAttachment::getStoredFileName)
                .collect(Collectors.toList());
        attachmentRepository.deleteAll(attachments);

        Runnable removeFiles = () -> {
            Path dir = Paths.get(uploadDir);
            for (String name : storedNames) {
                try {
                    Files.deleteIfExists(dir.resolve(Path.of(name).getFileName()));
                } catch (IOException ignored) {
                    // Best effort: the record is already gone; a leftover file is harmless.
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    removeFiles.run();
                }
            });
        } else {
            removeFiles.run();
        }
    }

    private AttachmentDto.Response mapToResponse(ComplaintAttachment a) {
        return AttachmentDto.Response.builder()
                .id(a.getId())
                .originalFileName(a.getOriginalFileName())
                .contentType(a.getContentType())
                .fileSize(a.getFileSize())
                .uploadedAt(a.getUploadedAt())
                .kind(a.getKind() != null ? a.getKind().name() : AttachmentKind.EVIDENCE.name())
                .uploadedByName(a.getUploadedBy() != null ? a.getUploadedBy().getName() : null)
                .uploadedByRole(a.getUploadedBy() != null ? a.getUploadedBy().getRole().name() : null)
                .build();
    }
}
