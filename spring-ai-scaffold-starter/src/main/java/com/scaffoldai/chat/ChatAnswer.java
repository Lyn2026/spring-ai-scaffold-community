package com.scaffoldai.chat;

import com.scaffoldai.model.TokenUsage;
import com.scaffoldai.rag.Citation;

import java.util.List;

/**
 * 对话结果（对外）。
 */
public record ChatAnswer(
        String requestId,
        String conversationId,
        String answer,
        String model,
        TokenUsage usage,
        List<Citation> citations,
        List<String> toolsUsed,
        long latencyMs
) {

    public ChatAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }
}
