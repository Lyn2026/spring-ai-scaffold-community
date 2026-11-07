package com.scaffoldai.model.provider;

import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.AiHttpClient;
import com.scaffoldai.model.ChatMessage;
import com.scaffoldai.model.ChatModel;
import com.scaffoldai.model.ChatRequest;
import com.scaffoldai.model.ChatResponse;
import com.scaffoldai.model.ChatStreamSink;
import com.scaffoldai.model.ModelCallException;
import com.scaffoldai.model.ProviderConfig;
import com.scaffoldai.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DashScope 原生协议适配器，用于官方 {@code https://dashscope.aliyuncs.com/api/v1}
 * 以及百炼专属部署（MaaS）端点 {@code https://ws-xxx.maas.aliyuncs.com/api/v1}。
 *
 * <p>这类端点和 compatible-mode 不是同一套协议：OpenAI 兼容的 {@code /chat/completions}
 * 在 MaaS 端点上是 404，正确的路径是 {@code /services/aigc/text-generation/generation}，
 * 请求体是 {@code input.messages + parameters}，响应是 {@code output.choices[0].message.content}。
 *
 * <p>原生协议这里不透传工具调用：需要 {@code @AiTool} 请改用 compatible-mode 端点。
 */
public class DashScopeNativeChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(DashScopeNativeChatModel.class);
    private static final String CHAT_PATH = "/services/aigc/text-generation/generation";
    private static final AtomicBoolean TOOL_IGNORED_WARNED = new AtomicBoolean(false);

    private final String logicalName;
    private final ProviderConfig config;
    private final AiHttpClient http;

    public DashScopeNativeChatModel(String logicalName, ProviderConfig config, AiHttpClient http) {
        this.logicalName = logicalName;
        this.config = config;
        this.http = http;
    }

    @Override
    public String name() {
        return logicalName;
    }

    @Override
    public String provider() {
        return "dashscope-native";
    }

    @Override
    public String modelName() {
        return TextUtils.defaultIfBlank(config.chatModel(), "qwen-plus");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        warnIfToolsIgnored(request);
        String url = AiHttpClient.requireUrl(config.baseUrl(), CHAT_PATH, logicalName);
        if (log.isDebugEnabled()) {
            log.debug("dashscope-native 请求 POST {} 逻辑名={} 真实模型={} 消息数={} temperature={} maxTokens={}",
                    url, logicalName, modelOf(request), request.messages().size(),
                    request.temperature(), request.maxTokens());
        }
        String raw = http.postJson(url, headers(false), buildBody(request, false), timeout());
        JsonNode root = JsonUtils.readTree(raw);
        assertNoError(root, "模型返回错误");
        JsonNode choice = root.path("output").path("choices").path(0);
        String content = contentOf(choice.path("message").path("content"));
        TokenUsage usage = parseUsage(root.path("usage"), request.messages(), content);
        if (log.isDebugEnabled()) {
            log.debug("dashscope-native 响应 真实模型={} finishReason={} 内容长度={} promptTokens={} completionTokens={}",
                    modelOf(request), choice.path("finish_reason").asText("stop"), content.length(),
                    usage.promptTokens(), usage.completionTokens());
        }
        return new ChatResponse(content, List.of(), usage, modelOf(request),
                choice.path("finish_reason").asText("stop"));
    }

    @Override
    public void stream(ChatRequest request, ChatStreamSink sink) {
        warnIfToolsIgnored(request);
        String url = AiHttpClient.requireUrl(config.baseUrl(), CHAT_PATH, logicalName);
        sink.onStart();
        StringBuilder collected = new StringBuilder();
        TokenUsage[] usageHolder = new TokenUsage[]{null};
        http.streamJsonLines(url, headers(true), buildBody(request, true),
                Duration.ofSeconds(Math.max(120, config.timeoutSeconds())), line -> {
                    String trimmed = line == null ? "" : line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                        return true;
                    }
                    if (trimmed.startsWith("data:")) {
                        trimmed = trimmed.substring(5).trim();
                    }
                    if ("[DONE]".equals(trimmed)) {
                        return false;
                    }
                    if (!trimmed.startsWith("{")) {
                        return true;
                    }
                    JsonNode node = JsonUtils.readTree(trimmed);
                    assertNoError(node, "模型流式返回错误");
                    JsonNode choice = node.path("output").path("choices").path(0);
                    String token = contentOf(choice.path("message").path("content"));
                    if (!token.isEmpty()) {
                        collected.append(token);
                        sink.onToken(token);
                    }
                    if (node.has("usage") && !node.path("usage").isNull()) {
                        usageHolder[0] = parseUsage(node.path("usage"), request.messages(), collected.toString());
                    }
                    return true;
                });
        TokenUsage usage = usageHolder[0] != null
                ? usageHolder[0]
                : TokenUsage.estimate(joinMessages(request.messages()), collected.toString());
        sink.onUsage(usage);
        sink.onFinish();
    }

    private Map<String, Object> buildBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelOf(request));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("messages", toWireMessages(request.messages()));
        body.put("input", input);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("result_format", "message");
        // Qwen3 系列默认开启思考，会吃掉 max_tokens 导致正文为空；对话场景默认关掉，需要推理时改这里
        if (modelOf(request).toLowerCase().startsWith("qwen3")) {
            parameters.put("enable_thinking", false);
        }
        Double temperature = request.temperature() != null ? request.temperature() : config.temperature();
        if (temperature != null && temperature > 0) {
            parameters.put("temperature", temperature);
        }
        Integer maxTokens = request.maxTokens() != null ? request.maxTokens() : config.maxTokens();
        if (maxTokens != null && maxTokens > 0) {
            parameters.put("max_tokens", maxTokens);
        }
        if (stream) {
            parameters.put("incremental_output", true);
        }
        body.put("parameters", parameters);
        return body;
    }

    private List<Map<String, Object>> toWireMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (!TextUtils.hasText(message.content())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role().wireName());
            item.put("content", message.content());
            wire.add(item);
        }
        return wire;
    }

    private String contentOf(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }
        // 多模态返回时 content 是数组，这里只拼文本片段
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item.isTextual()) {
                    sb.append(item.asText());
                } else if (item.has("text")) {
                    sb.append(item.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        return "";
    }

    private void assertNoError(JsonNode root, String prefix) {
        if (root.has("output")) {
            return;
        }
        String code = root.path("code").asText("");
        if (!code.isEmpty()) {
            throw new ModelCallException(prefix + " code=" + code + " message="
                    + root.path("message").asText("") + " requestId=" + root.path("request_id").asText(""));
        }
        if (root.has("error")) {
            throw new ModelCallException(prefix + ": " + root.path("error").path("message").asText(""));
        }
    }

    private TokenUsage parseUsage(JsonNode usage, List<ChatMessage> messages, String completion) {
        int prompt = usage.path("input_tokens").asInt(0);
        int completionTokens = usage.path("output_tokens").asInt(0);
        if (prompt <= 0 && completionTokens <= 0) {
            return TokenUsage.estimate(joinMessages(messages), completion);
        }
        return new TokenUsage(prompt, completionTokens);
    }

    private String joinMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            sb.append(message.content()).append('\n');
        }
        return sb.toString();
    }

    private String modelOf(ChatRequest request) {
        return TextUtils.defaultIfBlank(request.model(), modelName());
    }

    private void warnIfToolsIgnored(ChatRequest request) {
        if (request.hasTools() && TOOL_IGNORED_WARNED.compareAndSet(false, true)) {
            log.warn("dashscope-native 协议不透传工具调用，已忽略 {} 个工具；需要用 @AiTool 时请把 base-url 换成 "
                    + "https://dashscope.aliyuncs.com/compatible-mode/v1", request.tools().size());
        }
    }

    private Map<String, String> headers(boolean stream) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (TextUtils.hasText(config.apiKey())) {
            headers.put("Authorization", "Bearer " + config.apiKey().trim());
        }
        if (stream) {
            headers.put("X-DashScope-SSE", "enable");
            headers.put("Accept", "text/event-stream");
        }
        return headers;
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds());
    }
}
