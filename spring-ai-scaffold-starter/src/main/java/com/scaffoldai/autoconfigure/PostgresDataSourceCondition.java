package com.scaffoldai.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 判断当前数据源是否为 PostgreSQL，用于 rag.store=auto 时自动启用 pgvector。
 */
public class PostgresDataSourceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        if (url == null) {
            url = "";
        }
        if (url.startsWith("jdbc:postgresql")) {
            return ConditionOutcome.match("检测到 PostgreSQL 数据源");
        }
        return ConditionOutcome.noMatch("数据源不是 PostgreSQL: " + url);
    }
}
