package com.scaffoldai.model;

import java.util.List;
import java.util.Map;

/**
 * 统一对话请求。model 为空时使用适配层配置的默认模型名。
 */
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Integer maxTokens,
        List<ToolSpec> tools,
        Map<String, Object> extra
) {

    public ChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public static ChatRequest of(String model, List<ChatMessage> messages) {
        return new ChatRequest(model, messages, null, null, List.of(), Map.of());
    }

    public ChatRequest withTools(List<ToolSpec> tools) {
        return new ChatRequest(model, messages, temperature, maxTokens, tools, extra);
    }

    public ChatRequest withMessages(List<ChatMessage> messages) {
        return new ChatRequest(model, messages, temperature, maxTokens, tools, extra);
    }

    public ChatRequest withTemperature(Double temperature) {
        return new ChatRequest(model, messages, temperature, maxTokens, tools, extra);
    }

    public ChatRequest withMaxTokens(Integer maxTokens) {
        return new ChatRequest(model, messages, temperature, maxTokens, tools, extra);
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }
}
