package com.project.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class DepartmentDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank private String name;
        private String description;
        private String contactEmail;
        private String contactPhone;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private String contactEmail;
        private String contactPhone;
        private boolean active;
    }
}
