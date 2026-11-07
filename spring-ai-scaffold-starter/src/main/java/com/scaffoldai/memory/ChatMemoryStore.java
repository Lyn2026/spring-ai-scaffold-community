package com.scaffoldai.memory;

import com.scaffoldai.model.ChatMessage;

import java.util.List;

/**
 * 对话记忆存储 SPI。实现类需保证会话隔离：不同租户、不同会话互不可见。
 */
public interface ChatMemoryStore {

    /**
     * 存储类型标识，用于健康检查与文档展示：memory / redis / jdbc。
     */
    String type();

    List<ChatMessage> load(String tenantId, String conversationId, int limit);

    void append(String tenantId, String conversationId, List<ChatMessage> messages);

    void clear(String tenantId, String conversationId);

    List<ConversationSummary> listConversations(String tenantId, int limit);
}
