package com.scaffoldai.autoconfigure;

import com.scaffoldai.model.ModelRegistry;
import com.scaffoldai.rag.KnowledgeStore;
import com.scaffoldai.rag.PgVectorKnowledgeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PgVector 知识库：生产推荐实现。
 * rag.store=auto 且数据源为 PostgreSQL 时自动启用，也可显式配置 pgvector。
 */
@AutoConfiguration(before = AiScaffoldRagAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "ai.scaffold", name = "enabled", matchIfMissing = true)
public class AiScaffoldPgVectorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiScaffoldPgVectorConfiguration.class);

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(KnowledgeStore.class)
    @ConditionalOnProperty(prefix = "ai.scaffold.rag", name = "store", havingValue = "pgvector")
    public KnowledgeStore pgVectorKnowledgeStore(JdbcTemplate jdbcTemplate, ModelRegistry modelRegistry,
                                                 AiScaffoldProperties properties) {
        int dimension = modelRegistry.defaultEmbeddingModel().dimension();
        log.info("知识库使用 pgvector（dimension={}）", dimension);
        return new PgVectorKnowledgeStore(jdbcTemplate, dimension, properties.getRag().isAutoInitSchema());
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(KnowledgeStore.class)
    @ConditionalOnProperty(prefix = "ai.scaffold.rag", name = "store", havingValue = "auto")
    @Conditional(PostgresDataSourceCondition.class)
    public KnowledgeStore autoPgVectorKnowledgeStore(JdbcTemplate jdbcTemplate, ModelRegistry modelRegistry,
                                                     AiScaffoldProperties properties) {
        int dimension = modelRegistry.defaultEmbeddingModel().dimension();
        log.info("检测到 PostgreSQL，知识库自动启用 pgvector（dimension={}）", dimension);
        return new PgVectorKnowledgeStore(jdbcTemplate, dimension, properties.getRag().isAutoInitSchema());
    }
}
