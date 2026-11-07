package com.scaffoldai.ratelimit;

import java.time.Duration;

/**
 * 限流 SPI：默认固定窗口计数，可替换为令牌桶或网关侧限流。
 */
public interface RateLimiter {

    String type();

    RateLimitResult tryAcquire(String key, int limit, Duration window);

    default void reset(String key) {
    }
}
