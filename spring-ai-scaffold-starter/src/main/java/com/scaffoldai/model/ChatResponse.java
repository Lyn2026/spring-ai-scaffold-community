package com.scaffoldai.model;

import java.util.List;

/**
 * 统一对话响应。
 */
public record ChatResponse(
        String content,
        List<ToolCall> toolCalls,
        TokenUsage usage,
        String model,
        String finishReason
) {

    public ChatResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? TokenUsage.ZERO : usage;
        finishReason = finishReason == null ? "stop" : finishReason;
    }

    public static ChatResponse text(String content, TokenUsage usage, String model) {
        return new ChatResponse(content, List.of(), usage, model, "stop");
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public boolean isEmpty() {
        return content.isBlank() && toolCalls.isEmpty();
    }
}
