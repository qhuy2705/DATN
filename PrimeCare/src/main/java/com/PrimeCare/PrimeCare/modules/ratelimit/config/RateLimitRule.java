package com.PrimeCare.PrimeCare.modules.ratelimit.config;

public record RateLimitRule(String pathPrefix, String method, long limit, long windowSeconds, String eventType) {
}
