package com.PrimeCare.PrimeCare.modules.ratelimit.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RateLimitRuleResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String pathPattern;
    private String httpMethod;
    private String eventType;
    private Long limitCount;
    private Long windowSeconds;
    private Long bucketSeconds;
    private boolean enabled;
    private Integer priority;
    private Long defaultLimitCount;
    private Long defaultWindowSeconds;
    private Long defaultBucketSeconds;
    private boolean defaultEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
