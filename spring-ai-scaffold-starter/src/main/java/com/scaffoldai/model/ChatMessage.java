package com.scaffoldai.model;

import java.util.List;

/**
 * 统一消息模型。屏蔽各家模型协议的差异：
 * 助手发起的工具调用、工具执行结果都收敛到同一结构。
 */
public record ChatMessage(
        Role role,
        String content,
        String name,
        String toolCallId,
        List<ToolCall> toolCalls
) {

    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, null, List.of());
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, null, List.of());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null, null, List.of());
    }

    public static ChatMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, content, null, null, toolCalls);
    }

    public static ChatMessage toolResult(String toolCallId, String toolName, String content) {
        return new ChatMessage(Role.TOOL, content, toolName, toolCallId, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public boolean isEmpty() {
        return content.isBlank() && toolCalls.isEmpty();
    }
}
