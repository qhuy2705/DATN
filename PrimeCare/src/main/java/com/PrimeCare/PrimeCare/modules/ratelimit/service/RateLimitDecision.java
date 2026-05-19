package com.PrimeCare.PrimeCare.modules.ratelimit.service;

public record RateLimitDecision(
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds
) {
}
