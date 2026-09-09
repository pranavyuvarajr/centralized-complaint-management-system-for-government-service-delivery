package com.project.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class CategoryDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank private String name;
        private String description;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private boolean active;
    }
}
