package com.project.complaint.service;

import com.project.complaint.dto.AttachmentDto;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintAttachment;
import com.project.complaint.entity.User;
import com.project.complaint.repository.ComplaintAttachmentRepository;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String originalName = Path.of(file.getOriginalFilename()).getFileName().toString();
            String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
            String storedName = UUID.randomUUID() + extension;
            Files.copy(file.getInputStream(), dir.resolve(storedName));

            ComplaintAttachment attachment = attachmentRepository.save(ComplaintAttachment.builder()
                    .complaint(complaint)
                    .originalFileName(originalName)
                    .storedFileName(storedName)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build());

            return mapToResponse(attachment);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage());
        }
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

    private AttachmentDto.Response mapToResponse(ComplaintAttachment a) {
        return AttachmentDto.Response.builder()
                .id(a.getId())
                .originalFileName(a.getOriginalFileName())
                .contentType(a.getContentType())
                .fileSize(a.getFileSize())
                .uploadedAt(a.getUploadedAt())
                .build();
    }
}
