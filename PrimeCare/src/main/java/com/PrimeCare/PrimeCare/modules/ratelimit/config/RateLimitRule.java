package com.PrimeCare.PrimeCare.modules.ratelimit.config;

public record RateLimitRule(
        Long id,
        String pathPattern,
        String httpMethod,
        String eventType,
        long limitCount,
        long windowSeconds,
        long bucketSeconds,
        int priority
) {
    public boolean matches(String method, String path) {
        return path != null
                && method != null
                && path.startsWith(pathPattern)
                && method.equalsIgnoreCase(httpMethod);
    }
}
