package com.scaffoldai.autoconfigure;

import com.scaffoldai.memory.ChatMemoryStore;
import com.scaffoldai.memory.RedisChatMemoryStore;
import com.scaffoldai.rag.KnowledgeStore;
import com.scaffoldai.rag.RedisKnowledgeStore;
import com.scaffoldai.ratelimit.RateLimiter;
import com.scaffoldai.ratelimit.RedisRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 实现：对话记忆、分布式限流、轻量向量库。
 * 仅在引入 spring-data-redis 且存在 StringRedisTemplate 时生效。
 */
@AutoConfiguration(before = {AiScaffoldJdbcConfiguration.class, AiScaffoldAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "ai.scaffold", name = "enabled", matchIfMissing = true)
public class AiScaffoldRedisConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiScaffoldRedisConfiguration.class);

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.memory", name = "store", havingValue = "redis")
    public ChatMemoryStore redisChatMemoryStore(StringRedisTemplate redisTemplate, AiScaffoldProperties properties) {
        return buildRedisMemory(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.memory", name = "store", havingValue = "auto")
    public ChatMemoryStore autoRedisChatMemoryStore(StringRedisTemplate redisTemplate, AiScaffoldProperties properties) {
        return buildRedisMemory(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.rate-limit", name = "store", havingValue = "redis")
    public RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate) {
        log.info("限流使用 Redis 计数器");
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.rate-limit", name = "store", havingValue = "auto")
    public RateLimiter autoRedisRateLimiter(StringRedisTemplate redisTemplate) {
        log.info("限流自动使用 Redis 计数器");
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(KnowledgeStore.class)
    @ConditionalOnProperty(prefix = "ai.scaffold.rag", name = "store", havingValue = "redis")
    public KnowledgeStore redisKnowledgeStore(StringRedisTemplate redisTemplate, AiScaffoldProperties properties) {
        log.info("知识库使用 Redis 存储（应用侧余弦检索，建议分片数 < 1 万）");
        return new RedisKnowledgeStore(redisTemplate, properties.getRag().getKeyPrefix());
    }

    private ChatMemoryStore buildRedisMemory(StringRedisTemplate redisTemplate, AiScaffoldProperties properties) {
        log.info("对话记忆使用 Redis 存储（maxMessages={}, ttl={}min）",
                properties.getMemory().getMaxMessages(), properties.getMemory().getTtlMinutes());
        return new RedisChatMemoryStore(redisTemplate, properties.getMemory().getMaxMessages(),
                Duration.ofMinutes(Math.max(1, properties.getMemory().getTtlMinutes())),
                properties.getMemory().getKeyPrefix());
    }
}
