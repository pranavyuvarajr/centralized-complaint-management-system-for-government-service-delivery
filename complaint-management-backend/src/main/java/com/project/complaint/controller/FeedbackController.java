package com.project.complaint.controller;

import com.project.complaint.dto.FeedbackDto;
import com.project.complaint.service.AuditLogService;
import com.project.complaint.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/complaints/{complaintId}/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AuditLogService auditLogService;

    @PostMapping
    public ResponseEntity<FeedbackDto.Response> submit(@PathVariable Long complaintId, Authentication auth,
                                                         @Valid @RequestBody FeedbackDto.CreateRequest request) {
        FeedbackDto.Response saved = feedbackService.submitFeedback(complaintId, auth.getName(), request);
        auditLogService.logByEmail(auth.getName(), "FEEDBACK_SUBMITTED", "Complaint", complaintId,
                "Citizen submitted feedback (rating " + saved.getRating() + "/5) on complaint #" + complaintId);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<FeedbackDto.Response> get(@PathVariable Long complaintId) {
        return ResponseEntity.ok(feedbackService.getFeedback(complaintId));
    }
}
