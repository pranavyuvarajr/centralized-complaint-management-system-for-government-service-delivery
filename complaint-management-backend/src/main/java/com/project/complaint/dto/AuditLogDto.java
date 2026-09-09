package com.project.complaint.dto;

import lombok.*;
import java.time.LocalDateTime;

public class AuditLogDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String performedBy;
        private String action;
        private String entityType;
        private Long entityId;
        private String description;
        private LocalDateTime createdAt;
    }
}
