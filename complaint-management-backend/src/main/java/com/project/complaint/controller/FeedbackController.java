package com.project.complaint.controller;

import com.project.complaint.dto.FeedbackDto;
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

    @PostMapping
    public ResponseEntity<FeedbackDto.Response> submit(@PathVariable Long complaintId, Authentication auth,
                                                         @Valid @RequestBody FeedbackDto.CreateRequest request) {
        return ResponseEntity.ok(feedbackService.submitFeedback(complaintId, auth.getName(), request));
    }

    @GetMapping
    public ResponseEntity<FeedbackDto.Response> get(@PathVariable Long complaintId) {
        return ResponseEntity.ok(feedbackService.getFeedback(complaintId));
    }
}
