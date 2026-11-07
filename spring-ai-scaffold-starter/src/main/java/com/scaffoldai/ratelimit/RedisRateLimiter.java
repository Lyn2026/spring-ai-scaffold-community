package com.scaffoldai.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Redis 固定窗口限流：多实例部署共享计数，Lua 保证原子性。
 */
public class RedisRateLimiter implements RateLimiter {

    private static final String LUA =
            "local key = KEYS[1]\n"
                    + "local limit = tonumber(ARGV[1])\n"
                    + "local window = tonumber(ARGV[2])\n"
                    + "local count = redis.call('INCR', key)\n"
                    + "if count == 1 then redis.call('PEXPIRE', key, window) end\n"
                    + "return count\n";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public RateLimitResult tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return RateLimitResult.allow(0, 0);
        }
        Long count = redis.execute(script, List.of(key), String.valueOf(limit),
                String.valueOf(Math.max(1000, window.toMillis())));
        long used = count == null ? 0 : count;
        if (used > limit) {
            Long ttl = redis.getExpire(key);
            return RateLimitResult.deny(limit, ttl == null || ttl <= 0 ? 60 : ttl);
        }
        return RateLimitResult.allow(limit, Math.max(0, limit - used));
    }

    @Override
    public void reset(String key) {
        redis.delete(key);
    }
}
