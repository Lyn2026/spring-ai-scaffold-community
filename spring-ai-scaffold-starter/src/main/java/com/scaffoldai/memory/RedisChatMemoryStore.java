package com.scaffoldai.memory;

import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Redis 对话记忆：List 结构存消息，按会话裁剪窗口并设置 TTL，天然支持多实例共享。
 */
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redis;
    private final int maxMessages;
    private final Duration ttl;
    private final String prefix;
    private final ObjectMapper mapper = JsonUtils.mapper();

    public RedisChatMemoryStore(StringRedisTemplate redis, int maxMessages, Duration ttl, String prefix) {
        this.redis = redis;
        this.maxMessages = Math.max(2, maxMessages);
        this.ttl = ttl == null ? Duration.ofMinutes(30) : ttl;
        this.prefix = TextUtils.defaultIfBlank(prefix, "ai:mem:");
    }

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public List<ChatMessage> load(String tenantId, String conversationId, int limit) {
        int window = Math.min(Math.max(1, limit), maxMessages);
        List<String> raw = redis.opsForList().range(key(tenantId, conversationId), -window, -1);
        List<ChatMessage> messages = new ArrayList<>();
        if (raw == null) {
            return messages;
        }
        for (String item : raw) {
            try {
                StoredMessage stored = JsonUtils.fromJson(item, StoredMessage.class);
                if (stored != null && stored.message() != null) {
                    messages.add(stored.message());
                }
            } catch (RuntimeException ignore) {
                // 脏数据跳过，避免单个坏消息导致整段会话不可用
            }
        }
        return messages;
    }

    @Override
    public void append(String tenantId, String conversationId, List<ChatMessage> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return;
        }
        String key = key(tenantId, conversationId);
        List<String> payloads = new ArrayList<>();
        for (ChatMessage message : newMessages) {
            payloads.add(JsonUtils.toJson(new StoredMessage(message, System.currentTimeMillis())));
        }
        redis.opsForList().rightPushAll(key, payloads);
        redis.opsForList().trim(key, -maxMessages, -1);
        redis.expire(key, ttl);
    }

    @Override
    public void clear(String tenantId, String conversationId) {
        redis.delete(key(tenantId, conversationId));
    }

    @Override
    public List<ConversationSummary> listConversations(String tenantId, int limit) {
        List<ConversationSummary> result = new ArrayList<>();
        String pattern = prefix + tenantId + ":*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Long size = redis.opsForList().size(key);
                String last = redis.opsForList().index(key, -1);
                Instant lastAt = Instant.now();
                String preview = "";
                if (last != null) {
                    try {
                        StoredMessage stored = JsonUtils.fromJson(last, StoredMessage.class);
                        preview = TextUtils.truncate(stored.message().content(), 80);
                        lastAt = Instant.ofEpochMilli(stored.at());
                    } catch (RuntimeException ignore) {
                        // 忽略解析失败
                    }
                }
                result.add(new ConversationSummary(key.substring((prefix + tenantId + ":").length()),
                        size == null ? 0 : size.intValue(), lastAt, preview));
            }
        }
        result.sort(Comparator.comparing(ConversationSummary::lastActiveAt).reversed());
        return result.size() <= limit ? result : result.subList(0, limit);
    }

    private String key(String tenantId, String conversationId) {
        return prefix + tenantId + ":" + conversationId;
    }

    /**
     * 存储包装：附带写入时间，便于会话列表排序。
     */
    public record StoredMessage(ChatMessage message, long at) {
    }
}
