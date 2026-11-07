package com.scaffoldai.ratelimit;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;

import java.time.Duration;

/**
 * 限流服务：按租户与用户双层限流，超限抛出 RATE_LIMITED（HTTP 429）。
 */
public class RateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final boolean enabled;
    private final int tenantRpm;
    private final int userRpm;

    public RateLimitService(RateLimiter limiter, boolean enabled, int tenantRpm, int userRpm) {
        this.limiter = limiter;
        this.enabled = enabled;
        this.tenantRpm = tenantRpm;
        this.userRpm = userRpm;
    }

    public void check(String tenantId, String userId) {
        if (!enabled) {
            return;
        }
        if (tenantRpm > 0) {
            RateLimitResult result = limiter.tryAcquire(key("tenant", tenantId), tenantRpm, WINDOW);
            if (!result.allowed()) {
                throw new AiException(ErrorCode.RATE_LIMITED,
                        "租户请求频率超限（" + tenantRpm + " 次/分钟），请 " + result.retryAfterSeconds() + " 秒后重试");
            }
        }
        if (userRpm > 0 && userId != null) {
            RateLimitResult result = limiter.tryAcquire(key("user", tenantId + ":" + userId), userRpm, WINDOW);
            if (!result.allowed()) {
                throw new AiException(ErrorCode.RATE_LIMITED,
                        "用户请求频率超限（" + userRpm + " 次/分钟），请 " + result.retryAfterSeconds() + " 秒后重试");
            }
        }
    }

    public RateLimitResult peek(String tenantId) {
        return limiter.tryAcquire(key("tenant", tenantId), Math.max(0, tenantRpm), WINDOW);
    }

    public String limiterType() {
        return limiter.type();
    }

    private String key(String scope, String id) {
        return "ai:rl:" + scope + ":" + id;
    }
}
