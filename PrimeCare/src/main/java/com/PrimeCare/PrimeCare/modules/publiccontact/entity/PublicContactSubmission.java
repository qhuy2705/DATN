package com.PrimeCare.PrimeCare.modules.publiccontact.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "public_contact_submissions", indexes = {
        @Index(name = "idx_public_contact_created_at", columnList = "created_at"),
        @Index(name = "idx_public_contact_status", columnList = "status"),
        @Index(name = "idx_public_contact_status_created_at", columnList = "status, created_at")
})
public class PublicContactSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 40)
    private String referenceCode;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "source_page", length = 255)
    private String sourcePage;

    @Column(name = "requester_ip", length = 64)
    private String requesterIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "emailed_at")
    private LocalDateTime emailedAt;

    @Column(name = "email_error", length = 500)
    private String emailError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null || status.isBlank()) status = "RECEIVED";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
