package com.scaffoldai.model.provider;

import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.AiHttpClient;
import com.scaffoldai.model.EmbeddingModel;
import com.scaffoldai.model.ModelCallException;
import com.scaffoldai.model.ProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容向量协议适配器（DashScope text-embedding-v3/v4、OpenAI、vLLM 等）。
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiCompatibleEmbeddingModel.class);

    private final String logicalName;
    private final ProviderConfig config;
    private final AiHttpClient http;

    public OpenAiCompatibleEmbeddingModel(String logicalName, ProviderConfig config, AiHttpClient http) {
        this.logicalName = logicalName;
        this.config = config;
        this.http = http;
    }

    @Override
    public String name() {
        return logicalName;
    }

    @Override
    public int dimension() {
        return config.embeddingDimension();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String url = AiHttpClient.requireUrl(config.baseUrl(), "/embeddings", logicalName);
        if (log.isDebugEnabled()) {
            log.debug("openai-compatible 向量请求 POST {} 逻辑名={} 真实模型={} 文本数={} 维度={}",
                    url, logicalName, TextUtils.defaultIfBlank(config.embeddingModel(), "text-embedding-v3"),
                    texts.size(), config.embeddingDimension());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", TextUtils.defaultIfBlank(config.embeddingModel(), "text-embedding-v3"));
        body.put("input", texts);
        body.put("encoding_format", "float");

        Map<String, String> headers = new LinkedHashMap<>();
        if (TextUtils.hasText(config.apiKey())) {
            headers.put("Authorization", "Bearer " + config.apiKey().trim());
        }
        String raw = http.postJson(url, headers, body, Duration.ofSeconds(config.timeoutSeconds()));
        JsonNode root = JsonUtils.readTree(raw);
        if (root.has("error")) {
            throw new ModelCallException(root.path("error").path("message").asText("向量模型返回错误"));
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new ModelCallException("向量模型返回为空");
        }
        List<float[]> result = new ArrayList<>();
        for (JsonNode item : data) {
            result.add(parseVector(item.path("embedding")));
        }
        if (result.size() != texts.size()) {
            throw new ModelCallException("向量数量与输入不一致: expected=" + texts.size() + " actual=" + result.size());
        }
        return result;
    }

    static float[] parseVector(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new ModelCallException("向量字段为空");
        }
        float[] vector = new float[node.size()];
        for (int i = 0; i < node.size(); i++) {
            vector[i] = (float) node.get(i).asDouble();
        }
        return vector;
    }
}
