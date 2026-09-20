package com.project.complaint.controller;

import com.project.complaint.dto.ComplaintDto;
import com.project.complaint.dto.PageResponse;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.Priority;
import com.project.complaint.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // Multipart: the evidence photo travels with the rest of the form in one
    // request when present, but is only mandatory for categories an admin has
    // configured to require one - the service enforces that. Location
    // (latitude/longitude) is always required by the service, one way or another.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplaintDto.Response> createComplaint(
            Authentication auth,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String category,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        ComplaintDto.CreateRequest request = new ComplaintDto.CreateRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setCategory(category);
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        return ResponseEntity.ok(complaintService.createComplaint(auth.getName(), request, photo));
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

    /**
     * Marks a complaint resolved, with the completion photo in the same request.
     * Required when the category requires a citizen photo, optional otherwise;
     * staff only (checked in the service).
     */
    @PostMapping(value = "/{id}/resolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplaintDto.Response> resolve(
            @PathVariable Long id,
            Authentication auth,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String resolutionInfo,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.ok(complaintService.resolveComplaint(
                id, auth.getName(), roleOf(auth), remarks, resolutionInfo, photo));
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

    /** Reopen with an optional photo (multipart). The JSON variant above keeps working. */
    @PostMapping(value = "/{id}/reopen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplaintDto.Response> reopenWithPhoto(
            @PathVariable Long id,
            Authentication auth,
            @RequestParam(required = false) String reason,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.ok(complaintService.reopenComplaint(id, auth.getName(), reason, photo));
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
