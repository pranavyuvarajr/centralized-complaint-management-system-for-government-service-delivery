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

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank @Size(min = 6)
        private String newPassword;
    }
}
