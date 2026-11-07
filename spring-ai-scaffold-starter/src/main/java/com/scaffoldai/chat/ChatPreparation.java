package com.scaffoldai.chat;

import com.scaffoldai.model.ChatModel;
import com.scaffoldai.model.ChatMessage;
import com.scaffoldai.model.ToolSpec;
import com.scaffoldai.rag.RagContext;

import java.util.List;

/**
 * 对话准备结果（内部使用）：已经完成身份校验、限流、审核、检索与提示词拼装。
 */
public record ChatPreparation(
        String requestId,
        String tenantId,
        String userId,
        String conversationId,
        String question,
        String modelName,
        ChatModel model,
        List<ChatMessage> messages,
        RagContext ragContext,
        List<ToolSpec> tools,
        Double temperature,
        Integer maxTokens,
        long startedAtMillis
) {
}
