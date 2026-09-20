package com.project.complaint.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "complaint_categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    /**
     * The department complaints in this category are auto-routed to when a
     * citizen submits one. Nullable so existing/unmapped categories keep
     * falling back to the old admin-assigns-manually behavior.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Whether a citizen submitting a complaint in this category must attach
     * a photo. Defaults to true so existing categories (created before this
     * setting existed) keep the previous always-mandatory-photo behavior
     * unless an admin explicitly turns it off.
     */
    @Column(name = "image_required", nullable = false)
    @Builder.Default
    private boolean imageRequired = true;

    /**
     * Whether a citizen must mark a location (map pin / search / current
     * location) when filing a complaint in this category. Independent of the
     * photo. Defaults to true so existing categories keep the previous
     * always-mandatory-location behavior. The explicit column definition
     * carries a DEFAULT so Hibernate's ddl-auto=update can add this NOT NULL
     * column to a table that already has rows.
     */
    @Column(name = "location_required", nullable = false, columnDefinition = "boolean not null default true")
    @Builder.Default
    private boolean locationRequired = true;
}
