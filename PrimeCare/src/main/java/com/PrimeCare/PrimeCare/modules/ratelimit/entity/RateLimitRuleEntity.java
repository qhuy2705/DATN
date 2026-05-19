package com.PrimeCare.PrimeCare.modules.ratelimit.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "rate_limit_rules",
        uniqueConstraints = @UniqueConstraint(name = "uq_rate_limit_rules_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_rate_limit_rules_enabled_priority", columnList = "enabled, priority"),
                @Index(name = "idx_rate_limit_rules_path_method", columnList = "path_pattern, http_method"),
                @Index(name = "idx_rate_limit_rules_event_type", columnList = "event_type")
        }
)
public class RateLimitRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 128)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "path_pattern", nullable = false, length = 255)
    private String pathPattern;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "limit_count", nullable = false)
    private Long limitCount;

    @Column(name = "window_seconds", nullable = false)
    private Long windowSeconds;

    @Column(name = "bucket_seconds", nullable = false)
    private Long bucketSeconds;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "default_limit_count", nullable = false)
    private Long defaultLimitCount;

    @Column(name = "default_window_seconds", nullable = false)
    private Long defaultWindowSeconds;

    @Column(name = "default_bucket_seconds", nullable = false)
    private Long defaultBucketSeconds;

    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
