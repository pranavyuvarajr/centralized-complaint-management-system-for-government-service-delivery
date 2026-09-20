package com.project.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;

public class UserDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private String role;
        private Long departmentId;
        private String departmentName;
        private boolean active;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class UpdateProfileRequest {
        @NotBlank
        private String name;
        private String phone;
    }

    /**
     * Admin-side edit of another user. Email is deliberately not editable
     * (it is the login identity and the JWT subject), and role can't be
     * changed here either. departmentId only applies to officials; when it
     * differs from the official's current department, any active complaints
     * still assigned to them must be handed to another official
     * (reassignTo) or explicitly left unassigned (unassignComplaints).
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AdminUpdateRequest {
        @NotBlank
        private String name;
        private String phone;
        private Long departmentId;
        private Long reassignTo;
        private boolean unassignComplaints;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank @Size(min = 6)
        private String newPassword;
    }
}
