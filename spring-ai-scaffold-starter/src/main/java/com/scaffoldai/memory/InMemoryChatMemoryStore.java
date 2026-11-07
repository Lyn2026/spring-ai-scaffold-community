package com.scaffoldai.memory;

import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.ChatMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内记忆存储：零依赖，适合本地开发与单机小流量场景。重启即丢失。
 */
public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final int maxMessagesPerConversation;
    private final int maxConversations;
    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActive = new ConcurrentHashMap<>();

    public InMemoryChatMemoryStore(int maxMessagesPerConversation, int maxConversations) {
        this.maxMessagesPerConversation = Math.max(2, maxMessagesPerConversation);
        this.maxConversations = Math.max(10, maxConversations);
    }

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public List<ChatMessage> load(String tenantId, String conversationId, int limit) {
        List<ChatMessage> all = messages.get(key(tenantId, conversationId));
        if (all == null) {
            return List.of();
        }
        synchronized (all) {
            int from = Math.max(0, all.size() - Math.max(1, limit));
            return List.copyOf(all.subList(from, all.size()));
        }
    }

    @Override
    public void append(String tenantId, String conversationId, List<ChatMessage> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return;
        }
        String key = key(tenantId, conversationId);
        List<ChatMessage> list = messages.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (list) {
            list.addAll(newMessages);
            while (list.size() > maxMessagesPerConversation) {
                list.remove(0);
            }
        }
        lastActive.put(key, Instant.now());
        evictIfNecessary();
    }

    @Override
    public void clear(String tenantId, String conversationId) {
        String key = key(tenantId, conversationId);
        messages.remove(key);
        lastActive.remove(key);
    }

    @Override
    public List<ConversationSummary> listConversations(String tenantId, int limit) {
        String prefix = tenantId + ":";
        List<ConversationSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<ChatMessage>> entry : messages.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            List<ChatMessage> list = entry.getValue();
            String conversationId = entry.getKey().substring(prefix.length());
            int count;
            String preview;
            synchronized (list) {
                count = list.size();
                preview = count == 0 ? "" : list.get(count - 1).content();
            }
            result.add(new ConversationSummary(conversationId, count,
                    lastActive.getOrDefault(entry.getKey(), Instant.now()), TextUtils.truncate(preview, 80)));
        }
        result.sort(Comparator.comparing(ConversationSummary::lastActiveAt).reversed());
        return result.size() <= limit ? result : result.subList(0, limit);
    }

    private void evictIfNecessary() {
        if (messages.size() <= maxConversations) {
            return;
        }
        Map<String, Instant> sorted = new LinkedHashMap<>();
        lastActive.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        int toRemove = messages.size() - maxConversations;
        for (String key : sorted.keySet()) {
            if (toRemove-- <= 0) {
                break;
            }
            messages.remove(key);
            lastActive.remove(key);
        }
    }

    private String key(String tenantId, String conversationId) {
        return tenantId + ":" + conversationId;
    }
}
