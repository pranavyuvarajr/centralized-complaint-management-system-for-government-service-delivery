package com.project.complaint.controller;

import com.project.complaint.dto.ComplaintDto;
import com.project.complaint.dto.PageResponse;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.Priority;
import com.project.complaint.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    private String roleOf(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }

    @PostMapping
    public ResponseEntity<ComplaintDto.Response> createComplaint(
            Authentication auth,
            @Valid @RequestBody ComplaintDto.CreateRequest request) {
        return ResponseEntity.ok(complaintService.createComplaint(auth.getName(), request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ComplaintDto.Response>> getComplaints(
            Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String search) {
        String role = roleOf(auth);
        return ResponseEntity.ok(complaintService.getComplaints(auth.getName(), role, page, limit, status, category, priority, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComplaintDto.Response> getComplaint(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(complaintService.getComplaintById(id, auth.getName(), roleOf(auth)));
    }

    @GetMapping("/track/{complaintNumber}")
    public ResponseEntity<ComplaintDto.TrackResponse> track(@PathVariable String complaintNumber) {
        return ResponseEntity.ok(complaintService.trackByNumber(complaintNumber));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ComplaintDto.Response> updateStatus(
            @PathVariable Long id,
            Authentication auth,
            @Valid @RequestBody ComplaintDto.StatusUpdateRequest request) {
        return ResponseEntity.ok(complaintService.updateComplaintStatus(id, auth.getName(), roleOf(auth), request));
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<ComplaintDto.Response> updatePriority(
            @PathVariable Long id, Authentication auth, @RequestBody Map<String, String> body) {
        Priority priority = Priority.valueOf(body.get("priority").toUpperCase());
        return ResponseEntity.ok(complaintService.updatePriority(id, auth.getName(), roleOf(auth), priority));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<ComplaintDto.Response> reopen(@PathVariable Long id, Authentication auth,
                                                          @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(complaintService.reopenComplaint(id, auth.getName(), reason));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ComplaintDto.Response> close(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(complaintService.closeComplaint(id, auth.getName()));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ComplaintDto.HistoryResponse>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(complaintService.getComplaintHistory(id));
    }

    @GetMapping("/dashboard/citizen")
    public ResponseEntity<Map<String, Object>> citizenDashboard(Authentication auth) {
        return ResponseEntity.ok(complaintService.getCitizenStats(auth.getName()));
    }

    @GetMapping("/dashboard/official")
    public ResponseEntity<Map<String, Object>> officialDashboard(Authentication auth) {
        return ResponseEntity.ok(complaintService.getOfficialStats(auth.getName()));
    }
}
