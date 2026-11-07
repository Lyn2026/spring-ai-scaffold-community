package com.scaffoldai.model;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;
import com.scaffoldai.model.provider.DashScopeNativeChatModel;
import com.scaffoldai.model.provider.DashScopeNativeEmbeddingModel;
import com.scaffoldai.model.provider.OllamaChatModel;
import com.scaffoldai.model.provider.OllamaEmbeddingModel;
import com.scaffoldai.model.provider.OpenAiCompatibleChatModel;
import com.scaffoldai.model.provider.OpenAiCompatibleEmbeddingModel;

import java.util.Locale;

/**
 * 按配置创建模型适配器。新增供应商只需在这里注册 type。
 *
 * <p>DashScope 有两套协议：compatible-mode（OpenAI 兼容）与原生 /api/v1（含百炼 MaaS 专属部署）。
 * type 为 dashscope 时按 base-url 自动判定，也可显式写 {@code type: dashscope-native}。
 */
public final class ModelFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ModelFactory.class);

    private final AiHttpClient httpClient;

    public ModelFactory(AiHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ChatModel createChatModel(String name, ProviderConfig config) {
        String type = normalize(config.type());
        ChatModel model;
        if (isDashScopeNative(type, config.baseUrl())) {
            log.info("对话模型 [{}] 协议=dashscope-native 真实模型={} base-url={} 工具调用=不支持",
                    name, config.chatModel(), config.baseUrl());
            model = new DashScopeNativeChatModel(name, config, httpClient);
        } else {
            switch (type) {
                case "openai-compatible":
                case "openai":
                case "dashscope":
                case "qwen":
                case "deepseek":
                    model = new OpenAiCompatibleChatModel(name, type.equals("dashscope") ? "dashscope" : type, config, httpClient);
                    break;
                case "ollama":
                    model = new OllamaChatModel(name, "ollama", config, httpClient);
                    break;
                default:
                    throw new AiException(ErrorCode.INVALID_ARGUMENT,
                            "不支持的模型类型: " + config.type()
                                    + "，可选 openai-compatible / dashscope / dashscope-native / ollama");
            }
            log.info("对话模型 [{}] 协议={} 真实模型={} base-url={} 工具调用=支持",
                    name, type, config.chatModel(), config.baseUrl());
        }
        return model;
    }

    public EmbeddingModel createEmbeddingModel(String name, ProviderConfig config) {
        String type = normalize(config.type());
        if (isDashScopeNative(type, config.baseUrl())) {
            log.info("向量模型 [{}] 协议=dashscope-native 真实模型={} 维度={} base-url={}",
                    name, config.embeddingModel(), config.embeddingDimension(), config.baseUrl());
            return new DashScopeNativeEmbeddingModel(name, config, httpClient);
        }
        switch (type) {
            case "openai-compatible":
            case "openai":
            case "dashscope":
            case "qwen":
            case "deepseek":
                log.info("向量模型 [{}] 协议={} 真实模型={} 维度={} base-url={}",
                        name, type, config.embeddingModel(), config.embeddingDimension(), config.baseUrl());
                return new OpenAiCompatibleEmbeddingModel(name, config, httpClient);
            case "ollama":
                log.info("向量模型 [{}] 协议=ollama 真实模型={} 维度={} base-url={}",
                        name, config.embeddingModel(), config.embeddingDimension(), config.baseUrl());
                return new OllamaEmbeddingModel(name, config, httpClient);
            default:
                throw new AiException(ErrorCode.INVALID_ARGUMENT, "不支持的向量模型类型: " + config.type());
        }
    }

    /**
     * 判定是否走 DashScope 原生协议：
     * 显式 type=dashscope-native；或 type=dashscope 且 base-url 是原生 /api/v1 端点（非 compatible-mode）。
     */
    private boolean isDashScopeNative(String type, String baseUrl) {
        if ("dashscope-native".equals(type) || "dashscope-maas".equals(type)) {
            return true;
        }
        if (!"dashscope".equals(type) && !"qwen".equals(type)) {
            return false;
        }
        String base = baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
        if (base.isEmpty() || base.contains("compatible-mode")) {
            return false;
        }
        return base.contains("/api/v1");
    }

    private String normalize(String type) {
        return type == null ? "openai-compatible" : type.trim().toLowerCase(Locale.ROOT);
    }
}

