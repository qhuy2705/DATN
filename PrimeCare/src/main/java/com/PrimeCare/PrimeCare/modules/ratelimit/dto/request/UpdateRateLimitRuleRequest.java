package com.PrimeCare.PrimeCare.modules.ratelimit.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateRateLimitRuleRequest {
    private Long limitCount;
    private Long windowSeconds;
    private Long bucketSeconds;
    private String description;

    private boolean limitCountPresent;
    private boolean windowSecondsPresent;
    private boolean bucketSecondsPresent;
    private boolean descriptionPresent;

    @JsonIgnore
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public Long getLimitCount() {
        return limitCount;
    }

    public Long getWindowSeconds() {
        return windowSeconds;
    }

    public Long getBucketSeconds() {
        return bucketSeconds;
    }

    public String getDescription() {
        return description;
    }

    @JsonIgnore
    public boolean isLimitCountPresent() {
        return limitCountPresent;
    }

    @JsonIgnore
    public boolean isWindowSecondsPresent() {
        return windowSecondsPresent;
    }

    @JsonIgnore
    public boolean isBucketSecondsPresent() {
        return bucketSecondsPresent;
    }

    @JsonIgnore
    public boolean isDescriptionPresent() {
        return descriptionPresent;
    }

    @JsonIgnore
    public Map<String, Object> getUnknownFields() {
        return unknownFields;
    }

    public void setLimitCount(Long limitCount) {
        this.limitCount = limitCount;
        this.limitCountPresent = true;
    }

    public void setWindowSeconds(Long windowSeconds) {
        this.windowSeconds = windowSeconds;
        this.windowSecondsPresent = true;
    }

    public void setBucketSeconds(Long bucketSeconds) {
        this.bucketSeconds = bucketSeconds;
        this.bucketSecondsPresent = true;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonAnySetter
    public void setUnknownField(String field, Object value) {
        unknownFields.put(field, value);
    }

    @AssertTrue(message = "Only limitCount, windowSeconds, bucketSeconds, and description can be updated.")
    @JsonIgnore
    public boolean isAllowedFieldsOnly() {
        return unknownFields.isEmpty();
    }
}
