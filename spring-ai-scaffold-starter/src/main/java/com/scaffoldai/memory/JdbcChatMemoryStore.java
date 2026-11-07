package com.scaffoldai.memory;

import com.scaffoldai.common.Ids;
import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.ChatMessage;
import com.scaffoldai.model.Role;
import com.scaffoldai.model.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JDBC 对话记忆：消息落库，适合需要长期留存与审计的场景（PostgreSQL / MySQL / H2 均可）。
 */
public class JdbcChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcChatMemoryStore.class);

    private final JdbcTemplate jdbc;

    public JdbcChatMemoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureSchema();
    }

    @Override
    public String type() {
        return "jdbc";
    }

    @Override
    public List<ChatMessage> load(String tenantId, String conversationId, int limit) {
        List<ChatMessage> messages = jdbc.query(
                "SELECT role, content, name, tool_call_id, tool_calls FROM ai_message "
                        + "WHERE tenant_id = ? AND conversation_id = ? ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> mapMessage(rs), tenantId, conversationId, Math.max(1, limit));
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public void append(String tenantId, String conversationId, List<ChatMessage> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<Object[]> batch = new ArrayList<>();
        for (ChatMessage message : newMessages) {
            batch.add(new Object[]{
                    Ids.next(),
                    tenantId,
                    conversationId,
                    message.role().name(),
                    message.content(),
                    message.name(),
                    message.toolCallId(),
                    message.hasToolCalls() ? JsonUtils.toJson(message.toolCalls()) : null,
                    Timestamp.from(now)
            });
        }
        jdbc.batchUpdate("INSERT INTO ai_message "
                        + "(id, tenant_id, conversation_id, role, content, name, tool_call_id, tool_calls, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batch);
    }

    @Override
    public void clear(String tenantId, String conversationId) {
        jdbc.update("DELETE FROM ai_message WHERE tenant_id = ? AND conversation_id = ?", tenantId, conversationId);
    }

    @Override
    public List<ConversationSummary> listConversations(String tenantId, int limit) {
        return jdbc.query("SELECT m.conversation_id, COUNT(*) AS cnt, MAX(m.created_at) AS last_at, "
                        + "(SELECT x.content FROM ai_message x WHERE x.tenant_id = m.tenant_id "
                        + " AND x.conversation_id = m.conversation_id ORDER BY x.created_at DESC LIMIT 1) AS preview "
                        + "FROM ai_message m WHERE m.tenant_id = ? GROUP BY m.conversation_id "
                        + "ORDER BY last_at DESC LIMIT ?",
                (rs, rowNum) -> new ConversationSummary(
                        rs.getString("conversation_id"),
                        rs.getInt("cnt"),
                        rs.getTimestamp("last_at") == null ? Instant.now() : rs.getTimestamp("last_at").toInstant(),
                        TextUtils.truncate(rs.getString("preview"), 80)),
                tenantId, Math.max(1, limit));
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        Role role = Role.valueOf(rs.getString("role"));
        List<ToolCall> toolCalls = List.of();
        String rawCalls = rs.getString("tool_calls");
        if (TextUtils.hasText(rawCalls)) {
            toolCalls = JsonUtils.fromJson(rawCalls, new TypeReference<List<ToolCall>>() {
            });
        }
        return new ChatMessage(role, rs.getString("content"), rs.getString("name"),
                rs.getString("tool_call_id"), toolCalls);
    }

    private void ensureSchema() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS ai_message ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "tenant_id VARCHAR(64) NOT NULL,"
                    + "conversation_id VARCHAR(64) NOT NULL,"
                    + "role VARCHAR(16) NOT NULL,"
                    + "content VARCHAR(100000),"
                    + "name VARCHAR(128),"
                    + "tool_call_id VARCHAR(128),"
                    + "tool_calls VARCHAR(20000),"
                    + "created_at TIMESTAMP NOT NULL)");
            try {
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_message_conv ON ai_message (tenant_id, conversation_id, created_at)");
            } catch (RuntimeException ignore) {
                log.debug("创建 ai_message 索引跳过: {}", ignore.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("初始化 ai_message 表失败: {}", e.getMessage());
        }
    }
}
