package com.scaffoldai.model;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;
import com.scaffoldai.common.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型注册表：业务侧只按逻辑名取模型，不关心底层是 DashScope 还是本地 Ollama。
 */
public class ModelRegistry {

    private final Map<String, ChatModel> chatModels;
    private final Map<String, EmbeddingModel> embeddingModels;
    private final String defaultChatModel;
    private final String defaultEmbeddingModel;

    public ModelRegistry(Map<String, ChatModel> chatModels, Map<String, EmbeddingModel> embeddingModels,
                         String defaultChatModel, String defaultEmbeddingModel) {
        this.chatModels = new LinkedHashMap<>(chatModels == null ? Map.of() : chatModels);
        this.embeddingModels = new LinkedHashMap<>(embeddingModels == null ? Map.of() : embeddingModels);
        this.defaultChatModel = defaultChatModel;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    public ChatModel chatModel(String name) {
        String key = TextUtils.defaultIfBlank(name, defaultChatModel);
        ChatModel model = chatModels.get(key);
        if (model == null) {
            throw new AiException(ErrorCode.NOT_FOUND, "未找到对话模型: " + key + "，可用: " + chatModels.keySet());
        }
        return model;
    }

    public ChatModel defaultChatModel() {
        return chatModel(null);
    }

    public EmbeddingModel embeddingModel(String name) {
        String key = TextUtils.defaultIfBlank(name, defaultEmbeddingModel);
        EmbeddingModel model = embeddingModels.get(key);
        if (model == null) {
            throw new AiException(ErrorCode.NOT_FOUND, "未找到向量模型: " + key + "，可用: " + embeddingModels.keySet());
        }
        return model;
    }

    public EmbeddingModel defaultEmbeddingModel() {
        return embeddingModel(null);
    }

    public boolean hasChatModel(String name) {
        return chatModels.containsKey(TextUtils.defaultIfBlank(name, defaultChatModel));
    }

    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> list = new ArrayList<>();
        chatModels.forEach((name, model) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("provider", model.provider());
            item.put("model", model.modelName());
            item.put("default", name.equals(defaultChatModel));
            list.add(item);
        });
        return list;
    }

    public List<Map<String, Object>> describeEmbedding() {
        List<Map<String, Object>> list = new ArrayList<>();
        embeddingModels.forEach((name, model) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("dimension", model.dimension());
            item.put("default", name.equals(defaultEmbeddingModel));
            list.add(item);
        });
        return list;
    }
}
