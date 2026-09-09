package com.project.complaint.dto;

import jakarta.validation.constraints.*;
import lombok.*;

public class AuthDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank private String name;
        @NotBlank @Email private String email;
        private String phone;
        @NotBlank @Size(min = 6) private String password;
        private String role; // CITIZEN by default
        private Long departmentId; // only for OFFICIAL
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank @Email private String email;
        @NotBlank private String password;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AuthResponse {
        private String token;
        private Long id;
        private String name;
        private String email;
        private String role;
    }
}
