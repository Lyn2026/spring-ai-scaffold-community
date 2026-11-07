package com.scaffoldai.autoconfigure;

import com.scaffoldai.model.ModelRegistry;
import com.scaffoldai.rag.DocumentParser;
import com.scaffoldai.rag.DocumentParserFactory;
import com.scaffoldai.rag.InMemoryKnowledgeStore;
import com.scaffoldai.rag.KnowledgeBaseService;
import com.scaffoldai.rag.KnowledgeStore;
import com.scaffoldai.rag.NoopReranker;
import com.scaffoldai.rag.PdfDocumentParser;
import com.scaffoldai.rag.PlainTextDocumentParser;
import com.scaffoldai.rag.Reranker;
import com.scaffoldai.rag.Retriever;
import com.scaffoldai.rag.TextSplitter;
import com.scaffoldai.prompt.PromptRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * RAG 自动配置：文档解析、切分、向量化入库、检索。
 *
 * <p>本版检索 = 向量召回 + 关键词召回（混合排序），不含 LLM 重排；
 * 重排接口 {@link Reranker} 已留出，需要时可自行提供实现覆盖 NoopReranker。</p>
 */
@AutoConfiguration(before = AiScaffoldAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ai.scaffold", name = "enabled", matchIfMissing = true)
public class AiScaffoldRagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiScaffoldRagAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TextSplitter textSplitter(AiScaffoldProperties properties) {
        return new TextSplitter(properties.getRag().getChunkSize(), properties.getRag().getChunkOverlap());
    }

    @Bean
    @ConditionalOnMissingBean
    public PlainTextDocumentParser plainTextDocumentParser() {
        return new PlainTextDocumentParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public PdfDocumentParser pdfDocumentParser() {
        return new PdfDocumentParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentParserFactory documentParserFactory(List<DocumentParser> parsers) {
        return new DocumentParserFactory(parsers);
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeStore.class)
    public KnowledgeStore inMemoryKnowledgeStore() {
        log.warn("知识库使用进程内存储，重启后丢失。生产环境请配置 pgvector（PostgreSQL）或 redis。");
        return new InMemoryKnowledgeStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeBaseService knowledgeBaseService(KnowledgeStore knowledgeStore,
                                                     DocumentParserFactory documentParserFactory,
                                                     TextSplitter textSplitter,
                                                     ModelRegistry modelRegistry,
                                                     AiScaffoldProperties properties) {
        return new KnowledgeBaseService(knowledgeStore, documentParserFactory, textSplitter, modelRegistry,
                properties.getRag().getEmbeddingBatchSize(),
                properties.getRag().getMaxFileSizeMb() * 1024L * 1024L);
    }

    @Bean
    @ConditionalOnMissingBean
    public Reranker reranker(PromptRegistry promptRegistry) {
        // 占位实现直接返回原序；要接重排模型时提供自己的 Reranker Bean 即可覆盖
        return new NoopReranker();
    }

    @Bean
    @ConditionalOnMissingBean
    public Retriever retriever(KnowledgeStore knowledgeStore, ModelRegistry modelRegistry,
                               Reranker reranker, AiScaffoldProperties properties) {
        return new Retriever(knowledgeStore, modelRegistry, reranker,
                properties.getRag().getTopK(), properties.getRag().getMinScore(),
                properties.getRag().isHybridEnabled(), false);
    }
}
