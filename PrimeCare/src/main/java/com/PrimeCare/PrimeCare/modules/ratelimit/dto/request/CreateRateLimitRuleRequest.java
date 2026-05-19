package com.PrimeCare.PrimeCare.modules.ratelimit.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRateLimitRuleRequest {
    private String code;
    private String name;
    private String description;
    private String pathPattern;
    private String httpMethod;
    private String eventType;
    private Long limitCount;
    private Long windowSeconds;
    private Long bucketSeconds;
    private Boolean enabled = true;
    private Integer priority;
}
