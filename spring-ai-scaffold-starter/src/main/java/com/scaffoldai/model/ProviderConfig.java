package com.scaffoldai.model;

/**
 * 单个模型供应商配置（由 ai.scaffold.models.* 映射而来，与配置类解耦）。
 */
public record ProviderConfig(
        String name,
        String type,
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel,
        double temperature,
        int maxTokens,
        int timeoutSeconds,
        int embeddingDimension,
        String fallback
) {

    public ProviderConfig {
        type = type == null ? "openai-compatible" : type;
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        temperature = temperature <= 0 ? 0.3 : temperature;
        maxTokens = maxTokens <= 0 ? 2048 : maxTokens;
        timeoutSeconds = timeoutSeconds <= 0 ? 60 : timeoutSeconds;
        embeddingDimension = embeddingDimension <= 0 ? 1024 : embeddingDimension;
    }
}
