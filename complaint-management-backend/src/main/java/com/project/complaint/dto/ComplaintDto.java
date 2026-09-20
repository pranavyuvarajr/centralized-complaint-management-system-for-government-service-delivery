package com.project.complaint.dto;

import com.project.complaint.entity.ComplaintStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class ComplaintDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank @Size(min = 5, max = 150)
        private String title;
        @NotBlank @Size(min = 20, message = "Description must be at least 20 characters")
        private String description;
        @NotBlank
        private String category;
        // Fallback map-pin coordinates, used only when the submitted photo has
        // no GPS EXIF data of its own. Ignored (server prefers the photo's own
        // GPS) when the photo does carry a usable GPS tag.
        private Double latitude;
        private Double longitude;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class StatusUpdateRequest {
        @NotNull private ComplaintStatus status;
        private String remarks;
        private String resolutionInfo;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AssignRequest {
        @NotNull private Long departmentId;
        private Long officialId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String complaintNumber;
        private String title;
        private String description;
        private String category;
        private Double latitude;
        private Double longitude;
        private String resolvedAddress;
        private String priority;
        private String status;
        private String resolutionInfo;
        private String citizenName;
        private String citizenEmail;
        private Long departmentId;
        private String departmentName;
        private Long assignedOfficialId;
        private String assignedOfficialName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime closedAt;
        private List<HistoryResponse> history;
        // Whether marking this complaint resolved needs a completion photo (mirrors the
        // category's photo rule). Only filled in for single-complaint responses, null in lists.
        private Boolean completionPhotoRequired;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HistoryResponse {
        private Long id;
        private String status;
        private String remarks;
        private String updatedBy;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TrackResponse {
        private String complaintNumber;
        private String title;
        private String category;
        private String departmentName;
        private String status;
        private String priority;
        private LocalDateTime submittedAt;
        private LocalDateTime updatedAt;
        private List<HistoryResponse> history;
    }
}
