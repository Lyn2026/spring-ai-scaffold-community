package com.scaffoldai.autoconfigure;

import com.scaffoldai.audit.AuditService;
import com.scaffoldai.audit.AuditStore;
import com.scaffoldai.audit.InMemoryAuditStore;
import com.scaffoldai.chat.ChatService;
import com.scaffoldai.chat.ChatStreamService;
import com.scaffoldai.memory.ChatMemoryService;
import com.scaffoldai.memory.ChatMemoryStore;
import com.scaffoldai.memory.InMemoryChatMemoryStore;
import com.scaffoldai.metrics.AiMetrics;
import com.scaffoldai.model.AiHttpClient;
import com.scaffoldai.model.ChatModel;
import com.scaffoldai.model.EmbeddingModel;
import com.scaffoldai.model.ModelFactory;
import com.scaffoldai.model.ModelRegistry;
import com.scaffoldai.model.ProviderConfig;
import com.scaffoldai.moderation.KeywordModerator;
import com.scaffoldai.moderation.ModerationService;
import com.scaffoldai.prompt.PromptRegistry;
import com.scaffoldai.rag.KnowledgeBaseService;
import com.scaffoldai.rag.Retriever;
import com.scaffoldai.ratelimit.InMemoryRateLimiter;
import com.scaffoldai.ratelimit.RateLimitService;
import com.scaffoldai.ratelimit.RateLimiter;
import com.scaffoldai.tool.BuiltinTools;
import com.scaffoldai.tool.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 核心自动配置：模型层、记忆、工具、提示词、审核、限流、审计、对话编排。
 *
 * <p>Redis / JDBC / pgvector 专属实现放在各自的配置类，避免宿主未引入依赖时类加载失败。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.scaffold", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AiScaffoldProperties.class)
public class AiScaffoldAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiScaffoldAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AiHttpClient aiHttpClient(AiScaffoldProperties properties) {
        int timeout = 10;
        for (AiScaffoldProperties.Model model : properties.getModels().values()) {
            timeout = Math.max(timeout, model.getConnectTimeoutSeconds());
        }
        return new AiHttpClient(Duration.ofSeconds(timeout));
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelRegistry aiModelRegistry(AiScaffoldProperties properties, AiHttpClient aiHttpClient) {
        Map<String, AiScaffoldProperties.Model> effective = effectiveModels(properties);
        ModelFactory factory = new ModelFactory(aiHttpClient);
        Map<String, ChatModel> chats = new LinkedHashMap<>();
        Map<String, EmbeddingModel> embeddings = new LinkedHashMap<>();
        effective.forEach((name, model) -> {
            if (!model.isEnabled()) {
                return;
            }
            ProviderConfig provider = new ProviderConfig(name, model.getType(), model.getBaseUrl(),
                    model.getApiKey(), model.getChatModel(), model.getEmbeddingModel(),
                    model.getTemperature(), model.getMaxTokens(), model.getTimeoutSeconds(),
                    model.getEmbeddingDimension(), null);
            chats.put(name, factory.createChatModel(name, provider));
            embeddings.put(name, factory.createEmbeddingModel(name, provider));
        });

        log.info("AI 模型注册完成：chat={}, embedding={}, defaultChat={}, defaultEmbedding={}",
                chats.keySet(), embeddings.keySet(),
                properties.getDefaultChatModel(), properties.getDefaultEmbeddingModel());
        return new ModelRegistry(chats, embeddings, properties.getDefaultChatModel(), properties.getDefaultEmbeddingModel());
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatMemoryStore chatMemoryStore(AiScaffoldProperties properties) {
        log.info("对话记忆使用进程内存储（如需持久化请配置 ai.scaffold.memory.store=redis/jdbc）");
        return new InMemoryChatMemoryStore(properties.getMemory().getMaxMessages(), 10_000);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatMemoryService chatMemoryService(ChatMemoryStore store, AiScaffoldProperties properties) {
        return new ChatMemoryService(store, properties.getChat().getHistoryWindow());
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry aiToolRegistry(ApplicationContext applicationContext) {
        return new ToolRegistry(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptRegistry promptRegistry() {
        return new PromptRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModerationService moderationService(AiScaffoldProperties properties) {
        List<com.scaffoldai.moderation.Moderator> moderators = List.of(
                new KeywordModerator(properties.getModeration().getBlockedWords()));
        return new ModerationService(properties.getModeration().isEnabled(),
                properties.getModeration().getMaxInputChars(), moderators);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter() {
        return new InMemoryRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitService rateLimitService(RateLimiter rateLimiter, AiScaffoldProperties properties) {
        return new RateLimitService(rateLimiter, properties.getRateLimit().isEnabled(),
                properties.getRateLimit().getTenantRpm(), properties.getRateLimit().getUserRpm());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditStore auditStore(AiScaffoldProperties properties) {
        return new InMemoryAuditStore(properties.getAudit().getMemoryCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(AuditStore auditStore, AiScaffoldProperties properties) {
        return new AuditService(auditStore, properties.getAudit().isEnabled(),
                properties.getAudit().getTruncateLength());
    }

    @Bean
    @ConditionalOnMissingBean
    public AiMetrics aiMetrics(ObjectProvider<MeterRegistry> meterRegistries) {
        return new AiMetrics(meterRegistries.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public BuiltinTools builtinTools(Retriever retriever) {
        return new BuiltinTools(retriever);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatService chatService(ModelRegistry modelRegistry, ChatMemoryService chatMemoryService,
                                   Retriever retriever, KnowledgeBaseService knowledgeBaseService,
                                   ToolRegistry toolRegistry, PromptRegistry promptRegistry,
                                   ModerationService moderationService, RateLimitService rateLimitService,
                                   AuditService auditService, AiMetrics aiMetrics,
                                   AiScaffoldProperties properties) {
        return new ChatService(modelRegistry, chatMemoryService, retriever, knowledgeBaseService, toolRegistry,
                promptRegistry, moderationService, rateLimitService, auditService, aiMetrics,
                properties.getChat().getMaxToolRounds());
    }

    @Bean(name = "aiStreamExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "aiStreamExecutor")
    public ExecutorService aiStreamExecutor(AiScaffoldProperties properties) {
        int size = Math.max(1, properties.getChat().getStreamExecutorSize());
        AtomicInteger counter = new AtomicInteger(1);
        return Executors.newFixedThreadPool(size, runnable -> {
            Thread thread = new Thread(runnable, "ai-sse-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatStreamService chatStreamService(ChatService chatService,
                                              @Qualifier("aiStreamExecutor") ExecutorService aiStreamExecutor) {
        return new ChatStreamService(chatService, aiStreamExecutor);
    }

    /**
     * 未配置任何模型时给出可直接跑通的默认组合（DashScope 兼容模式 + 本地 Ollama）。
     */
    private Map<String, AiScaffoldProperties.Model> effectiveModels(AiScaffoldProperties properties) {
        if (!properties.getModels().isEmpty()) {
            return properties.getModels();
        }
        Map<String, AiScaffoldProperties.Model> defaults = new LinkedHashMap<>();
        AiScaffoldProperties.Model dashscope = new AiScaffoldProperties.Model();
        dashscope.setType("dashscope");
        dashscope.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        dashscope.setApiKey(System.getenv("DASHSCOPE_API_KEY"));
        dashscope.setChatModel("qwen-plus");
        dashscope.setEmbeddingModel("text-embedding-v4");
        dashscope.setEmbeddingDimension(1024);
        defaults.put("dashscope", dashscope);

        AiScaffoldProperties.Model ollama = new AiScaffoldProperties.Model();
        ollama.setType("ollama");
        ollama.setBaseUrl("http://localhost:11434");
        ollama.setChatModel("qwen2.5:7b");
        ollama.setEmbeddingModel("nomic-embed-text");
        ollama.setEmbeddingDimension(768);
        defaults.put("ollama", ollama);
        return defaults;
    }
}
