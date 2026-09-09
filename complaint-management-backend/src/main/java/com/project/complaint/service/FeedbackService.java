package com.project.complaint.service;

import com.project.complaint.dto.FeedbackDto;
import com.project.complaint.entity.Complaint;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.Feedback;
import com.project.complaint.entity.User;
import com.project.complaint.repository.ComplaintRepository;
import com.project.complaint.repository.FeedbackRepository;
import com.project.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    @Transactional
    public FeedbackDto.Response submitFeedback(Long complaintId, String email, FeedbackDto.CreateRequest request) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!complaint.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You can only give feedback on your own complaints");
        }
        if (complaint.getStatus() != ComplaintStatus.RESOLVED && complaint.getStatus() != ComplaintStatus.CLOSED) {
            throw new RuntimeException("Feedback can only be submitted after a complaint is resolved");
        }
        if (feedbackRepository.existsByComplaintId(complaintId)) {
            throw new RuntimeException("Feedback has already been submitted for this complaint");
        }

        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .complaint(complaint)
                .rating(request.getRating())
                .comment(request.getComment())
                .build());

        return mapToResponse(feedback);
    }

    public FeedbackDto.Response getFeedback(Long complaintId) {
        return feedbackRepository.findByComplaintId(complaintId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    private FeedbackDto.Response mapToResponse(Feedback f) {
        return FeedbackDto.Response.builder()
                .id(f.getId())
                .rating(f.getRating())
                .comment(f.getComment())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
