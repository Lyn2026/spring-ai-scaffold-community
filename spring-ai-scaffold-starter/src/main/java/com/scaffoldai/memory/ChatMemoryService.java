package com.scaffoldai.memory;

import com.scaffoldai.model.ChatMessage;

import java.util.List;

/**
 * 会话记忆服务：业务层只跟它打交道，底层 memory/redis/jdbc 由自动配置决定。
 */
public class ChatMemoryService {

    private final ChatMemoryStore store;
    private final int defaultWindow;

    public ChatMemoryService(ChatMemoryStore store, int defaultWindow) {
        this.store = store;
        this.defaultWindow = Math.max(2, defaultWindow);
    }

    public List<ChatMessage> history(String tenantId, String conversationId) {
        return history(tenantId, conversationId, defaultWindow);
    }

    public List<ChatMessage> history(String tenantId, String conversationId, int window) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return store.load(tenantId, conversationId, Math.min(window, defaultWindow));
    }

    public void appendUser(String tenantId, String conversationId, String question) {
        store.append(tenantId, conversationId, List.of(ChatMessage.user(question)));
    }

    public void appendAssistant(String tenantId, String conversationId, String answer) {
        store.append(tenantId, conversationId, List.of(ChatMessage.assistant(answer)));
    }

    public void recordExchange(String tenantId, String conversationId, String question, String answer) {
        store.append(tenantId, conversationId, List.of(ChatMessage.user(question), ChatMessage.assistant(answer)));
    }

    public void clear(String tenantId, String conversationId) {
        store.clear(tenantId, conversationId);
    }

    public List<ConversationSummary> conversations(String tenantId, int limit) {
        return store.listConversations(tenantId, Math.max(1, limit));
    }

    public ChatMemoryStore store() {
        return store;
    }
}
