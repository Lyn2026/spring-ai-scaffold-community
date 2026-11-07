package com.scaffoldai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * JDBC 审计存储：默认落库实现，同时承担用量统计（按天/模型）。
 */
public class JdbcAuditStore implements AuditStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditStore.class);

    private final JdbcTemplate jdbc;

    public JdbcAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureSchema();
    }

    @Override
    public String type() {
        return "jdbc";
    }

    @Override
    public void save(AuditRecord record) {
        jdbc.update("INSERT INTO ai_audit_log (id, request_id, tenant_id, user_id, conversation_id, model, question, answer, "
                        + "prompt_tokens, completion_tokens, total_tokens, latency_ms, status, error_code, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                record.id(), record.requestId(), record.tenantId(), record.userId(), record.conversationId(),
                record.model(), record.question(), record.answer(), record.promptTokens(), record.completionTokens(),
                record.totalTokens(), record.latencyMs(), record.status(), record.errorCode(),
                Timestamp.from(record.createdAt()));
    }

    @Override
    public List<AuditRecord> recent(String tenantId, int limit) {
        return jdbc.query("SELECT id, request_id, tenant_id, user_id, conversation_id, model, question, answer, "
                        + "prompt_tokens, completion_tokens, total_tokens, latency_ms, status, error_code, created_at "
                        + "FROM ai_audit_log WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, Math.max(1, limit));
    }

    @Override
    public List<UsageStat> stats(String tenantId, int days) {
        Instant from = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        // 按天聚合放在 Java 侧完成：CAST(ts AS DATE) 在 H2 / MySQL / PG 上语法与语义不一致，
        // 用纯 SQL 容易在某个库上直接报错，这里换取跨库稳定。
        List<UsageStat> rows = jdbc.query(
                "SELECT model, prompt_tokens, completion_tokens, created_at FROM ai_audit_log "
                        + "WHERE tenant_id = ? AND created_at >= ?",
                (rs, rowNum) -> {
                    Timestamp created = rs.getTimestamp("created_at");
                    String day = created == null ? "" : created.toLocalDateTime().toLocalDate().toString();
                    return new UsageStat(day, rs.getString("model"), 1L,
                            rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"));
                },
                tenantId, Timestamp.from(from));

        java.util.Map<String, UsageStat> aggregated = new java.util.LinkedHashMap<>();
        for (UsageStat row : rows) {
            String key = row.day() + "|" + row.model();
            UsageStat previous = aggregated.get(key);
            if (previous == null) {
                aggregated.put(key, row);
            } else {
                aggregated.put(key, new UsageStat(row.day(), row.model(), previous.requests() + 1,
                        previous.promptTokens() + row.promptTokens(),
                        previous.completionTokens() + row.completionTokens()));
            }
        }
        List<UsageStat> result = new java.util.ArrayList<>(aggregated.values());
        result.sort(java.util.Comparator.comparing(UsageStat::day).reversed()
                .thenComparing(UsageStat::model, java.util.Comparator.nullsLast(String::compareTo)));
        return result;
    }

    private void ensureSchema() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS ai_audit_log ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "request_id VARCHAR(64),"
                    + "tenant_id VARCHAR(64) NOT NULL,"
                    + "user_id VARCHAR(128),"
                    + "conversation_id VARCHAR(64),"
                    + "model VARCHAR(128),"
                    + "question VARCHAR(4000),"
                    + "answer VARCHAR(4000),"
                    + "prompt_tokens INT,"
                    + "completion_tokens INT,"
                    + "total_tokens INT,"
                    + "latency_ms BIGINT,"
                    + "status VARCHAR(32),"
                    + "error_code VARCHAR(256),"
                    + "created_at TIMESTAMP NOT NULL)");
            try {
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_audit_tenant ON ai_audit_log (tenant_id, created_at)");
            } catch (RuntimeException ignore) {
                log.debug("创建审计索引跳过: {}", ignore.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("初始化 ai_audit_log 表失败: {}", e.getMessage());
        }
    }

    private static final RowMapper<AuditRecord> MAPPER = (rs, rowNum) -> new AuditRecord(
            rs.getString("id"), rs.getString("request_id"), rs.getString("tenant_id"), rs.getString("user_id"),
            rs.getString("conversation_id"), rs.getString("model"), rs.getString("question"), rs.getString("answer"),
            rs.getInt("prompt_tokens"), rs.getInt("completion_tokens"), rs.getInt("total_tokens"),
            rs.getLong("latency_ms"), rs.getString("status"), rs.getString("error_code"),
            rs.getTimestamp("created_at") == null ? Instant.now() : rs.getTimestamp("created_at").toInstant());
}
