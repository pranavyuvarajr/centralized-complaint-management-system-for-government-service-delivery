package com.project.complaint.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // ---- Alert email (OFFICIAL / ADMIN): used for urgent-complaint emails, only when verified ----
    @Column(name = "alert_email")
    private String alertEmail;

    @Column(name = "alert_email_verified", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean alertEmailVerified = false;

    /** SHA-256 hash of the verification token; the raw token is only ever in the email link. */
    @Column(name = "alert_email_token_hash")
    private String alertEmailTokenHash;

    @Column(name = "alert_email_token_expires_at")
    private LocalDateTime alertEmailTokenExpiresAt;

    @Column(name = "alert_email_token_sent_at")
    private LocalDateTime alertEmailTokenSentAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
