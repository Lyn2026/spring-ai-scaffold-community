package com.scaffoldai.autoconfigure;

import com.scaffoldai.audit.AuditStore;
import com.scaffoldai.audit.JdbcAuditStore;
import com.scaffoldai.memory.ChatMemoryStore;
import com.scaffoldai.memory.JdbcChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC 实现（PostgreSQL / MySQL / H2 均可）：对话记忆、审计。
 * 仅在引入 spring-jdbc 且存在 JdbcTemplate 时生效。
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class},
        before = AiScaffoldAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "ai.scaffold", name = "enabled", matchIfMissing = true)
public class AiScaffoldJdbcConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiScaffoldJdbcConfiguration.class);

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.memory", name = "store", havingValue = "jdbc")
    public ChatMemoryStore jdbcChatMemoryStore(JdbcTemplate jdbcTemplate, AiScaffoldProperties properties) {
        log.info("对话记忆使用 JDBC 存储（maxMessages={}）", properties.getMemory().getMaxMessages());
        return new JdbcChatMemoryStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.memory", name = "store", havingValue = "auto")
    public ChatMemoryStore autoJdbcChatMemoryStore(JdbcTemplate jdbcTemplate, AiScaffoldProperties properties) {
        log.info("对话记忆自动选择 JDBC 存储（maxMessages={}）", properties.getMemory().getMaxMessages());
        return new JdbcChatMemoryStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.audit", name = "store", havingValue = "jdbc")
    public AuditStore jdbcAuditStore(JdbcTemplate jdbcTemplate) {
        log.info("审计与用量统计使用 JDBC 存储");
        return new JdbcAuditStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.scaffold.audit", name = "store", havingValue = "auto")
    public AuditStore autoJdbcAuditStore(JdbcTemplate jdbcTemplate) {
        log.info("审计与用量统计自动使用 JDBC 存储");
        return new JdbcAuditStore(jdbcTemplate);
    }
}
