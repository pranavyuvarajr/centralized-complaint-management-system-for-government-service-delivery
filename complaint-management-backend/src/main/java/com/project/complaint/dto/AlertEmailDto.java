package com.project.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class AlertEmailDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SetRequest {
        @NotBlank
        private String alertEmail;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class VerifyRequest {
        @NotBlank
        private String token;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Status {
        private String alertEmail;
        private boolean alertEmailVerified;
    }
}
