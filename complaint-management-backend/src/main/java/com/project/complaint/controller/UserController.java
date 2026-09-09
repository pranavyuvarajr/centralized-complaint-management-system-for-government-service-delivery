package com.project.complaint.controller;

import com.project.complaint.dto.UserDto;
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

    @GetMapping
    public ResponseEntity<UserDto.Response> getProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getProfile(auth.getName()));
    }

    @PatchMapping
    public ResponseEntity<UserDto.Response> updateProfile(Authentication auth, @Valid @RequestBody UserDto.UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(auth.getName(), request));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication auth, @Valid @RequestBody UserDto.ChangePasswordRequest request) {
        userService.changePassword(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
