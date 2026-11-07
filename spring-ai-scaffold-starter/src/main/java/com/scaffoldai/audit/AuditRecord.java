package com.scaffoldai.audit;

import java.time.Instant;

/**
 * 单次调用审计记录。问题与答案按配置截断，避免审计表膨胀过快。
 */
public record AuditRecord(
        String id,
        String requestId,
        String tenantId,
        String userId,
        String conversationId,
        String model,
        String question,
        String answer,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        String status,
        String errorCode,
        Instant createdAt
) {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_BLOCKED = "BLOCKED";
}
