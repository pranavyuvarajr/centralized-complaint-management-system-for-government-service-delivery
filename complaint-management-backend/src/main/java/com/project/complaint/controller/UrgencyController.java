package com.project.complaint.controller;

import com.project.complaint.dto.ComplaintDto;
import com.project.complaint.dto.UrgencyDto;
import com.project.complaint.service.ComplaintService;
import com.project.complaint.service.UrgencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Urgent-complaint verification. The decision is open to the assigned official or any admin (checked
 * in UrgencyService); the override lives under /api/admin/** so SecurityConfig restricts it to ADMIN.
 */
@RestController
@RequiredArgsConstructor
public class UrgencyController {

    private final UrgencyService urgencyService;
    private final ComplaintService complaintService;

    private String roleOf(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }

    @PostMapping("/api/complaints/{id}/urgency/decision")
    public ResponseEntity<ComplaintDto.Response> decide(
            @PathVariable Long id, Authentication auth, @RequestBody UrgencyDto.DecisionRequest request) {
        String role = roleOf(auth);
        urgencyService.decide(id, auth.getName(), role, request.getDecision(), request.getNote());
        return ResponseEntity.ok(complaintService.getComplaintById(id, auth.getName(), role));
    }

    @PatchMapping("/api/admin/complaints/{id}/urgency")
    public ResponseEntity<ComplaintDto.Response> override(
            @PathVariable Long id, Authentication auth, @RequestBody UrgencyDto.DecisionRequest request) {
        urgencyService.overrideDecision(id, auth.getName(), request.getDecision(), request.getNote());
        return ResponseEntity.ok(complaintService.getComplaintById(id, auth.getName(), "ADMIN"));
    }
}
