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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope 原生向量协议适配器，对应端点
 * {@code POST {base}/services/embeddings/text-embedding/text-embedding}。
 *
 * <p>请求体是 {@code input.texts + parameters.dimension}，响应是
 * {@code output.embeddings[i].embedding}，与 compatible-mode 的 {@code /embeddings} 不同。
 */
public class DashScopeNativeEmbeddingModel implements EmbeddingModel {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DashScopeNativeEmbeddingModel.class);

    private static final String EMBEDDING_PATH = "/services/embeddings/text-embedding/text-embedding";

    private final String logicalName;
    private final ProviderConfig config;
    private final AiHttpClient http;

    public DashScopeNativeEmbeddingModel(String logicalName, ProviderConfig config, AiHttpClient http) {
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
        String url = AiHttpClient.requireUrl(config.baseUrl(), EMBEDDING_PATH, logicalName);
        if (log.isDebugEnabled()) {
            log.debug("dashscope-native 向量请求 POST {} 逻辑名={} 真实模型={} 文本数={} 维度={}",
                    url, logicalName, TextUtils.defaultIfBlank(config.embeddingModel(), "text-embedding-v4"),
                    texts.size(), config.embeddingDimension());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", TextUtils.defaultIfBlank(config.embeddingModel(), "text-embedding-v4"));
        body.put("input", Map.of("texts", texts));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("dimension", config.embeddingDimension());
        parameters.put("text_type", "document");
        body.put("parameters", parameters);

        Map<String, String> headers = new LinkedHashMap<>();
        if (TextUtils.hasText(config.apiKey())) {
            headers.put("Authorization", "Bearer " + config.apiKey().trim());
        }
        String raw = http.postJson(url, headers, body, Duration.ofSeconds(config.timeoutSeconds()));
        JsonNode root = JsonUtils.readTree(raw);
        if (!root.has("output")) {
            String code = root.path("code").asText("");
            throw new ModelCallException("向量模型返回错误 code=" + code + " message="
                    + root.path("message").asText(""));
        }
        JsonNode embeddings = root.path("output").path("embeddings");
        if (!embeddings.isArray() || embeddings.isEmpty()) {
            throw new ModelCallException("向量模型返回为空");
        }
        List<JsonNode> sorted = new ArrayList<>();
        embeddings.forEach(sorted::add);
        List<JsonNode> ordered = new ArrayList<>(sorted);
        ordered.sort(Comparator.comparingInt(node -> node.path("text_index").asInt(0)));

        List<float[]> result = new ArrayList<>();
        for (JsonNode item : ordered) {
            result.add(OpenAiCompatibleEmbeddingModel.parseVector(item.path("embedding")));
        }
        if (result.size() != texts.size()) {
            throw new ModelCallException("向量数量与输入不一致: expected=" + texts.size() + " actual=" + result.size());
        }
        return result;
    }
}
