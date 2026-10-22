package com.project.complaint.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaints_urgency_status", columnList = "urgency_status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_number", unique = true)
    private String complaintNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_official_id")
    private User assignedOfficial;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category;

    // Location is a coordinate pair the citizen marked on the map (pin, address
    // search or current location) — independent of any photo. It is null when the
    // complaint's category doesn't require a location and none was given.
    private Double latitude;
    private Double longitude;

    @Column(name = "resolved_address", columnDefinition = "TEXT")
    private String resolvedAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;

    @Column(name = "resolution_info", columnDefinition = "TEXT")
    private String resolutionInfo;

    // ---- Urgent-complaint feature (all additive: DB-level defaults keep existing rows valid) ----

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_status", nullable = false, columnDefinition = "varchar(32) not null default 'NONE'")
    @Builder.Default
    private UrgencyStatus urgencyStatus = UrgencyStatus.NONE;

    /** The citizen's justification for marking the complaint urgent. Never erased, even after a downgrade. */
    @Column(name = "urgency_reason", columnDefinition = "TEXT")
    private String urgencyReason;

    @Column(name = "was_initially_urgent", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean wasInitiallyUrgent = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "urgency_decided_by_id")
    private User urgencyDecidedBy;

    @Column(name = "urgency_decided_at")
    private LocalDateTime urgencyDecidedAt;

    @Column(name = "urgency_decision_note", columnDefinition = "TEXT")
    private String urgencyDecisionNote;

    /** True once an admin has overridden a locked decision. */
    @Column(name = "urgency_overridden", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean urgencyOverridden = false;

    /** Start of the reminder clock: when the current official was assigned (or submission time if unassigned). */
    @Column(name = "urgency_assigned_at")
    private LocalDateTime urgencyAssignedAt;

    @Column(name = "reminder_count", nullable = false, columnDefinition = "integer not null default 0")
    @Builder.Default
    private int reminderCount = 0;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    @Column(name = "admin_escalated_at")
    private LocalDateTime adminEscalatedAt;

    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("updatedAt DESC")
    @Builder.Default
    private List<ComplaintHistory> history = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = ComplaintStatus.SUBMITTED;
        if (this.priority == null) this.priority = Priority.MEDIUM;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
