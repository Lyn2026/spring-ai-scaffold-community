package com.scaffoldai.model;

/**
 * 对话模型统一接口。实现者负责协议差异（OpenAI 兼容 / Ollama / 私有模型）。
 */
public interface ChatModel {

    /**
     * 逻辑名，唯一，用于路由与降级链。
     */
    String name();

    /**
     * 供应商标识，如 dashscope、ollama。
     */
    String provider();

    /**
     * 模型标识，如 qwen-plus、llama3。
     */
    String modelName();

    ChatResponse chat(ChatRequest request);

    void stream(ChatRequest request, ChatStreamSink sink);
}
