package com.project.complaint.controller;

import com.project.complaint.dto.AlertEmailDto;
import com.project.complaint.dto.UserDto;
import com.project.complaint.service.AlertEmailService;
import com.project.complaint.service.AuditLogService;
import com.project.complaint.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final AlertEmailService alertEmailService;

    @GetMapping
    public ResponseEntity<UserDto.Response> getProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getProfile(auth.getName()));
    }

    @PatchMapping
    public ResponseEntity<UserDto.Response> updateProfile(Authentication auth, @Valid @RequestBody UserDto.UpdateProfileRequest request) {
        UserDto.Response saved = userService.updateProfile(auth.getName(), request);
        auditLogService.logByEmail(auth.getName(), "PROFILE_UPDATED", "User", saved.getId(),
                saved.getName() + " (" + saved.getEmail() + ") updated their own profile details");
        return ResponseEntity.ok(saved);
    }

    // ---- Alert email (OFFICIAL / ADMIN) for urgent-complaint notifications ----

    @PutMapping("/alert-email")
    public ResponseEntity<AlertEmailDto.Status> setAlertEmail(Authentication auth, @Valid @RequestBody AlertEmailDto.SetRequest request) {
        AlertEmailDto.Status status = alertEmailService.set(auth.getName(), request.getAlertEmail());
        auditLogService.logByEmail(auth.getName(), "ALERT_EMAIL_SET", "User", null, auth.getName() + " set an alert email (pending verification)");
        return ResponseEntity.ok(status);
    }

    @PostMapping("/alert-email/resend")
    public ResponseEntity<AlertEmailDto.Status> resendAlertEmail(Authentication auth) {
        return ResponseEntity.ok(alertEmailService.resend(auth.getName()));
    }

    @DeleteMapping("/alert-email")
    public ResponseEntity<AlertEmailDto.Status> clearAlertEmail(Authentication auth) {
        AlertEmailDto.Status status = alertEmailService.clear(auth.getName());
        auditLogService.logByEmail(auth.getName(), "ALERT_EMAIL_CLEARED", "User", null, auth.getName() + " cleared their alert email");
        return ResponseEntity.ok(status);
    }


    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication auth, @Valid @RequestBody UserDto.ChangePasswordRequest request) {
        userService.changePassword(auth.getName(), request);
        auditLogService.logByEmail(auth.getName(), "PASSWORD_CHANGED", "User", null,
                auth.getName() + " changed their own password");
        return ResponseEntity.noContent().build();
    }
}
