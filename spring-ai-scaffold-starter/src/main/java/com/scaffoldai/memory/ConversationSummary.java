package com.scaffoldai.memory;

import java.time.Instant;

/**
 * 会话列表项。
 */
public record ConversationSummary(String conversationId, int messageCount, Instant lastActiveAt, String preview) {
}
