package com.scaffoldai.web;

import com.scaffoldai.rag.Citation;

import java.util.List;

/**
 * 对话响应体（非流式）。
 */
public record ChatResponseDto(String requestId, String conversationId, String answer, String model,
                              int promptTokens, int completionTokens, int totalTokens,
                              List<Citation> citations, List<String> toolsUsed, long latencyMs,
                              String status) {
}
