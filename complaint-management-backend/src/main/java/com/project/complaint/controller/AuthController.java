package com.project.complaint.controller;

import com.project.complaint.dto.AlertEmailDto;
import com.project.complaint.dto.AuthDto;
import com.project.complaint.service.AlertEmailService;
import com.project.complaint.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AlertEmailService alertEmailService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /** Public (under /api/auth/**): confirms an alert email from the link in the verification mail. POST so link scanners cannot consume the token. */
    @PostMapping("/verify-alert-email")
    public ResponseEntity<Void> verifyAlertEmail(@Valid @RequestBody AlertEmailDto.VerifyRequest request) {
        alertEmailService.verify(request.getToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
