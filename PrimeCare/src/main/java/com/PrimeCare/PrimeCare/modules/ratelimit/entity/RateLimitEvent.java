package com.PrimeCare.PrimeCare.modules.ratelimit.entity;

import com.PrimeCare.PrimeCare.shared.enums.RateLimitKeyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name="rate_limit_events",
        indexes = @Index(name="idx_rl", columnList="key_type,key_value,event_type,created_at")
)
public class RateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name="key_type", nullable=false, length=16)
    private RateLimitKeyType keyType;

    @Column(name="key_value", nullable=false, length=64)
    private String keyValue;

    @Column(name="event_type", nullable=false, length=64)
    private String eventType;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
