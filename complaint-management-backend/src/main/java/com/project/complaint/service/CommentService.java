package com.project.complaint.service;

import com.project.complaint.dto.CommentDto;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintComment;
import com.project.complaint.entity.User;
import com.project.complaint.repository.ComplaintCommentRepository;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final ComplaintCommentRepository commentRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
    public CommentDto.Response addComment(Long complaintId, String email, String role, CommentDto.CreateRequest request) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        checkAccess(complaint, user, role);

        ComplaintComment comment = commentRepository.save(ComplaintComment.builder()
                .complaint(complaint)
                .user(user)
                .message(request.getMessage())
                .build());

        User notifyTarget = role.equals("CITIZEN") ? complaint.getAssignedOfficial() : complaint.getUser();
        if (notifyTarget != null) {
            notificationService.notify(notifyTarget,
                    "New message on complaint " + complaint.getComplaintNumber() + " from " + user.getName() + ".",
                    complaint.getId());
        }

        return mapToResponse(comment);
    }

    public List<CommentDto.Response> getComments(Long complaintId, String email, String role) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        checkAccess(complaint, user, role);

        return commentRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private CommentDto.Response mapToResponse(ComplaintComment c) {
        return CommentDto.Response.builder()
                .id(c.getId())
                .message(c.getMessage())
                .authorName(c.getUser().getName())
                .authorRole(c.getUser().getRole().name())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
