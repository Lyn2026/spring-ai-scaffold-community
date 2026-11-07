package com.scaffoldai.ratelimit;

/**
 * 限流结果。
 */
public record RateLimitResult(boolean allowed, int limit, long remaining, long retryAfterSeconds) {

    public static RateLimitResult allow(int limit, long remaining) {
        return new RateLimitResult(true, limit, remaining, 0);
    }

    public static RateLimitResult deny(int limit, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, Math.max(1, retryAfterSeconds));
    }
}
