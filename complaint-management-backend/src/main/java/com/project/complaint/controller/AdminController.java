package com.project.complaint.controller;

import com.project.complaint.dto.AuditLogDto;
import com.project.complaint.dto.AuthDto;
import com.project.complaint.dto.ComplaintDto;
import com.project.complaint.dto.UserDto;
import com.project.complaint.entity.ComplaintStatus;
import com.project.complaint.entity.Priority;
import com.project.complaint.service.AuditLogService;
import com.project.complaint.service.AuthService;
import com.project.complaint.service.ComplaintService;
import com.project.complaint.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AuthService authService;
    private final ComplaintService complaintService;
    private final AuditLogService auditLogService;

    // --- User Management ---

    @GetMapping("/users")
    public ResponseEntity<List<UserDto.Response>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/role/{role}")
    public ResponseEntity<List<UserDto.Response>> getUsersByRole(@PathVariable String role) {
        return ResponseEntity.ok(userService.getUsersByRole(role));
    }

    @PostMapping("/users")
    public ResponseEntity<AuthDto.AuthResponse> createUser(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PatchMapping("/users/{id}/active")
    public ResponseEntity<UserDto.Response> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                                        Authentication auth) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        UserDto.Response before = userService.getUserById(id);
        UserDto.Response result = userService.setActive(id, active);
        auditLogService.logByEmail(auth.getName(), active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "User", id, "Admin " + (active ? "activated" : "deactivated") + " "
                        + before.getName() + " (" + before.getEmail() + ")");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                            @RequestParam(required = false) Long reassignTo,
                                            Authentication auth) {
        UserDto.Response target = userService.getUserById(id);
        userService.deleteUser(id, reassignTo, auth.getName());
        auditLogService.logByEmail(auth.getName(), "USER_DELETED", "User", id,
                "Admin permanently deleted " + target.getName() + " (" + target.getEmail() + ", " + target.getRole() + ")");
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body,
                                               Authentication auth) {
        UserDto.Response target = userService.getUserById(id);
        userService.adminSetPassword(id, body.get("newPassword"), auth.getName(), body.get("adminCurrentPassword"));
        auditLogService.logByEmail(auth.getName(), "USER_PASSWORD_RESET", "User", id,
                "Admin reset password for " + target.getName() + " (" + target.getEmail() + ")");
        return ResponseEntity.noContent().build();
    }

    // --- Complaint Assignment & Priority ---

    @PatchMapping("/complaints/{id}/assign")
    public ResponseEntity<ComplaintDto.Response> assignComplaint(
            @PathVariable Long id, @Valid @RequestBody ComplaintDto.AssignRequest request, Authentication auth) {
        return ResponseEntity.ok(complaintService.assignComplaint(id, request, auth.getName()));
    }

    @PatchMapping("/complaints/{id}/priority")
    public ResponseEntity<ComplaintDto.Response> updatePriority(
            @PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        Priority priority = Priority.valueOf(body.get("priority").toUpperCase());
        return ResponseEntity.ok(complaintService.updatePriority(id, auth.getName(), "ADMIN", priority));
    }

    // --- Dashboard Stats ---

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalComplaints", complaintService.countAll());
        stats.put("submitted", complaintService.countByStatus(ComplaintStatus.SUBMITTED));
        stats.put("underReview", complaintService.countByStatus(ComplaintStatus.UNDER_REVIEW));
        stats.put("assigned", complaintService.countByStatus(ComplaintStatus.ASSIGNED));
        stats.put("inProgress", complaintService.countByStatus(ComplaintStatus.IN_PROGRESS));
        stats.put("resolved", complaintService.countByStatus(ComplaintStatus.RESOLVED));
        stats.put("closed", complaintService.countByStatus(ComplaintStatus.CLOSED));
        stats.put("rejected", complaintService.countByStatus(ComplaintStatus.REJECTED));
        stats.put("byCategory", complaintService.countByCategory());
        stats.put("byDepartment", complaintService.countByDepartmentName());
        stats.put("monthlyVolume", complaintService.monthlyVolume());
        return ResponseEntity.ok(stats);
    }

    // --- Audit Logs ---

    @GetMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<AuditLogDto.Response> result = auditLogService.getLogs(PageRequest.of(Math.max(page - 1, 0), limit));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", result.getContent());
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", result.getTotalElements());
        pagination.put("totalPages", result.getTotalPages());
        body.put("pagination", pagination);
        return ResponseEntity.ok(body);
    }
}
