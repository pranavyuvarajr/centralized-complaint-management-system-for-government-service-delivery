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

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
