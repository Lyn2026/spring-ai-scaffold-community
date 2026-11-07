package com.scaffoldai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚手架配置项，前缀 ai.scaffold。
 *
 * <pre>
 * ai:
 *   scaffold:
 *     default-chat-model: dashscope
 *     models:
 *       dashscope:
 *         type: dashscope
 *         base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
 *         api-key: ${DASHSCOPE_API_KEY}
 *         chat-model: qwen-plus
 *         embedding-model: text-embedding-v4
 *       ollama:
 *         type: ollama
 *         base-url: http://localhost:11434
 *         chat-model: qwen2.5:7b
 *         embedding-model: nomic-embed-text
 * </pre>
 */
@ConfigurationProperties(prefix = "ai.scaffold")
public class AiScaffoldProperties {

    private boolean enabled = true;
    private String defaultChatModel = "dashscope";
    private String defaultEmbeddingModel = "dashscope";
    private Map<String, Model> models = new LinkedHashMap<>();
    private Chat chat = new Chat();
    private Memory memory = new Memory();
    private Rag rag = new Rag();
    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();
    private Moderation moderation = new Moderation();
    private Audit audit = new Audit();
    private Web web = new Web();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultChatModel() {
        return defaultChatModel;
    }

    public void setDefaultChatModel(String defaultChatModel) {
        this.defaultChatModel = defaultChatModel;
    }

    public String getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    public Map<String, Model> getModels() {
        return models;
    }

    public void setModels(Map<String, Model> models) {
        this.models = models;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Moderation getModeration() {
        return moderation;
    }

    public void setModeration(Moderation moderation) {
        this.moderation = moderation;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    public Web getWeb() {
        return web;
    }

    public void setWeb(Web web) {
        this.web = web;
    }

    /**
     * 单个模型供应商配置。
     */
    public static class Model {
        /** openai-compatible / dashscope / dashscope-native / ollama */
        private String type = "openai-compatible";
        private String baseUrl;
        private String apiKey;
        private String chatModel;
        private String embeddingModel;
        private double temperature = 0.3;
        private int maxTokens = 2048;
        private int timeoutSeconds = 60;
        private int connectTimeoutSeconds = 10;
        private int embeddingDimension = 1024;
        private boolean enabled = true;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getEmbeddingDimension() {
            return embeddingDimension;
        }

        public void setEmbeddingDimension(int embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Chat {
        /** 工具调用最多几轮，防止无限循环 */
        private int maxToolRounds = 4;
        /** 带进上下文的历史消息条数 */
        private int historyWindow = 20;
        /** SSE 超时秒数 */
        private int sseTimeoutSeconds = 120;
        /** SSE 线程池大小 */
        private int streamExecutorSize = 8;

        public int getMaxToolRounds() {
            return maxToolRounds;
        }

        public void setMaxToolRounds(int maxToolRounds) {
            this.maxToolRounds = maxToolRounds;
        }

        public int getHistoryWindow() {
            return historyWindow;
        }

        public void setHistoryWindow(int historyWindow) {
            this.historyWindow = historyWindow;
        }

        public int getSseTimeoutSeconds() {
            return sseTimeoutSeconds;
        }

        public void setSseTimeoutSeconds(int sseTimeoutSeconds) {
            this.sseTimeoutSeconds = sseTimeoutSeconds;
        }

        public int getStreamExecutorSize() {
            return streamExecutorSize;
        }

        public void setStreamExecutorSize(int streamExecutorSize) {
            this.streamExecutorSize = streamExecutorSize;
        }
    }

    public static class Memory {
        /** auto / redis / jdbc / memory */
        private String store = "auto";
        private int maxMessages = 40;
        private int ttlMinutes = 1440;
        private String keyPrefix = "ai:mem:";

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    public static class Rag {
        /** auto / pgvector / redis / memory */
        private String store = "auto";
        private int topK = 5;
        private double minScore = 0.45;
        private int chunkSize = 900;
        private int chunkOverlap = 150;
        private int maxFileSizeMb = 10;
        private int embeddingBatchSize = 16;
        /** 向量召回 + 关键词召回混合 */
        private boolean hybridEnabled = true;
        private boolean autoInitSchema = true;
        private String keyPrefix = "ai:kb:";

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public int getEmbeddingBatchSize() {
            return embeddingBatchSize;
        }

        public void setEmbeddingBatchSize(int embeddingBatchSize) {
            this.embeddingBatchSize = embeddingBatchSize;
        }

        public boolean isHybridEnabled() {
            return hybridEnabled;
        }

        public void setHybridEnabled(boolean hybridEnabled) {
            this.hybridEnabled = hybridEnabled;
        }

        public boolean isAutoInitSchema() {
            return autoInitSchema;
        }

        public void setAutoInitSchema(boolean autoInitSchema) {
            this.autoInitSchema = autoInitSchema;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    public static class Security {
        /** 本版只支持 header 透传；api-key 鉴权属企业版 */
        private String mode = "header";
        private String tenantHeader = "X-Tenant-Id";
        private String userHeader = "X-User-Id";
        private String rolesHeader = "X-Roles";
        private String defaultTenant = "public";
        private String defaultUser = "anonymous";
        private String defaultRoles = "user";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getTenantHeader() {
            return tenantHeader;
        }

        public void setTenantHeader(String tenantHeader) {
            this.tenantHeader = tenantHeader;
        }

        public String getUserHeader() {
            return userHeader;
        }

        public void setUserHeader(String userHeader) {
            this.userHeader = userHeader;
        }

        public String getRolesHeader() {
            return rolesHeader;
        }

        public void setRolesHeader(String rolesHeader) {
            this.rolesHeader = rolesHeader;
        }

        public String getDefaultTenant() {
            return defaultTenant;
        }

        public void setDefaultTenant(String defaultTenant) {
            this.defaultTenant = defaultTenant;
        }

        public String getDefaultUser() {
            return defaultUser;
        }

        public void setDefaultUser(String defaultUser) {
            this.defaultUser = defaultUser;
        }

        public String getDefaultRoles() {
            return defaultRoles;
        }

        public void setDefaultRoles(String defaultRoles) {
            this.defaultRoles = defaultRoles;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int tenantRpm = 600;
        private int userRpm = 60;
        /** auto / redis / memory */
        private String store = "auto";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTenantRpm() {
            return tenantRpm;
        }

        public void setTenantRpm(int tenantRpm) {
            this.tenantRpm = tenantRpm;
        }

        public int getUserRpm() {
            return userRpm;
        }

        public void setUserRpm(int userRpm) {
            this.userRpm = userRpm;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }
    }

    public static class Moderation {
        private boolean enabled = true;
        private int maxInputChars = 4000;
        private List<String> blockedWords = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
        }

        public List<String> getBlockedWords() {
            return blockedWords;
        }

        public void setBlockedWords(List<String> blockedWords) {
            this.blockedWords = blockedWords;
        }
    }

    public static class Audit {
        private boolean enabled = true;
        /** auto / jdbc / memory */
        private String store = "auto";
        private int truncateLength = 2000;
        private int memoryCapacity = 5000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public int getTruncateLength() {
            return truncateLength;
        }

        public void setTruncateLength(int truncateLength) {
            this.truncateLength = truncateLength;
        }

        public int getMemoryCapacity() {
            return memoryCapacity;
        }

        public void setMemoryCapacity(int memoryCapacity) {
            this.memoryCapacity = memoryCapacity;
        }
    }

    public static class Web {
        private boolean enabled = true;
        private String basePath = "/api/ai";
        private String corsAllowedOrigins = "*";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public String getCorsAllowedOrigins() {
            return corsAllowedOrigins;
        }

        public void setCorsAllowedOrigins(String corsAllowedOrigins) {
            this.corsAllowedOrigins = corsAllowedOrigins;
        }
    }
}
