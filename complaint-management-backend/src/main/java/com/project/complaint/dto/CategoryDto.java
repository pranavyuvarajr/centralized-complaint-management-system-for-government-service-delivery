package com.project.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class CategoryDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank private String name;
        private String description;
        // Department complaints in this category auto-route to. Null = no auto-routing (admin assigns manually).
        private Long departmentId;
        // Whether citizens must attach a photo when filing a complaint in this
        // category. Boolean (not boolean) so "not sent" can be told apart from
        // "explicitly false" - the service defaults a missing value to true.
        private Boolean imageRequired;
        // Whether citizens must mark a location for this category. Boolean so
        // "not sent" (treated as required) differs from an explicit false.
        private Boolean locationRequired;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private boolean active;
        private Long departmentId;
        private String departmentName;
        private boolean imageRequired;
        private boolean locationRequired;
    }
}
