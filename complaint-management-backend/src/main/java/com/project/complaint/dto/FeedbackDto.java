package com.project.complaint.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

public class FeedbackDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotNull @Min(1) @Max(5)
        private Integer rating;
        private String comment;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private Integer rating;
        private String comment;
        private LocalDateTime createdAt;
    }
}
