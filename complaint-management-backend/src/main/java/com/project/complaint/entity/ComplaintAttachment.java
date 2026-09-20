package com.project.complaint.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false)
    private String storedFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /**
     * What this file is for. The explicit column definition carries a DEFAULT so
     * Hibernate's ddl-auto=update can add this NOT NULL column to a table that
     * already has rows; every pre-existing attachment becomes EVIDENCE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, columnDefinition = "varchar(20) not null default 'EVIDENCE'")
    @Builder.Default
    private AttachmentKind kind = AttachmentKind.EVIDENCE;

    /** Who uploaded it. Null for attachments that predate this field. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
