package com.project.complaint.dto;

import lombok.*;
import java.time.LocalDateTime;

public class AttachmentDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String originalFileName;
        private String contentType;
        private Long fileSize;
        private LocalDateTime uploadedAt;
    }
}
