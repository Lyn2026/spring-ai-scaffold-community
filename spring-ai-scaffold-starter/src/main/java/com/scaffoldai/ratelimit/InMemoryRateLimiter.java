package com.scaffoldai.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内固定窗口限流：单实例足够用，集群部署请切换 redis 实现。
 */
public class InMemoryRateLimiter implements RateLimiter {

    private static final int MAX_KEYS = 100_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public RateLimitResult tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return RateLimitResult.allow(0, 0);
        }
        long windowMillis = Math.max(1000, window.toMillis());
        long now = System.currentTimeMillis();
        long windowIndex = now / windowMillis;
        Window current = windows.compute(key, (k, old) -> {
            if (old == null || old.index != windowIndex) {
                return new Window(windowIndex, new AtomicInteger(1));
            }
            old.count.incrementAndGet();
            return old;
        });
        int used = current.count.get();
        if (used > limit) {
            long retryAfter = ((windowIndex + 1) * windowMillis - now) / 1000;
            return RateLimitResult.deny(limit, retryAfter);
        }
        if (windows.size() > MAX_KEYS) {
            windows.clear();
        }
        return RateLimitResult.allow(limit, limit - used);
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    private record Window(long index, AtomicInteger count) {
    }
}
