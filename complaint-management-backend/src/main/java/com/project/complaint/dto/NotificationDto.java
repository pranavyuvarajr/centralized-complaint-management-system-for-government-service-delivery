package com.project.complaint.dto;

import lombok.*;
import java.time.LocalDateTime;

public class NotificationDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String message;
        private Long complaintId;
        private boolean read;
        private LocalDateTime createdAt;
    }
}
