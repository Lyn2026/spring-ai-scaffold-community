package com.scaffoldai.audit;

import com.scaffoldai.common.Ids;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * 审计服务：统一写入成功/失败/拦截三种记录。审计失败不影响主流程。
 */
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditStore store;
    private final boolean enabled;
    private final int truncateLength;

    public AuditService(AuditStore store, boolean enabled, int truncateLength) {
        this.store = store;
        this.enabled = enabled;
        this.truncateLength = truncateLength <= 0 ? 2000 : truncateLength;
    }

    public void record(String requestId, String tenantId, String userId, String conversationId, String model,
                       String question, String answer, TokenUsage usage, long latencyMs,
                       String status, String errorCode) {
        if (!enabled) {
            return;
        }
        TokenUsage safeUsage = usage == null ? TokenUsage.ZERO : usage;
        AuditRecord record = new AuditRecord(Ids.next(), requestId, tenantId, userId, conversationId, model,
                TextUtils.truncate(question, truncateLength), TextUtils.truncate(answer, truncateLength),
                safeUsage.promptTokens(), safeUsage.completionTokens(), safeUsage.totalTokens(),
                latencyMs, status, TextUtils.truncate(errorCode, 200), Instant.now());
        try {
            store.save(record);
        } catch (RuntimeException e) {
            log.warn("审计写入失败: {}", e.getMessage());
        }
    }

    public List<AuditRecord> recent(String tenantId, int limit) {
        return store.recent(tenantId, limit);
    }

    public List<UsageStat> stats(String tenantId, int days) {
        return store.stats(tenantId, days);
    }

    public String storeType() {
        return store.type();
    }
}
