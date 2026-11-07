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
import com.scaffoldai.model.Role;
import com.scaffoldai.model.TokenUsage;
import com.scaffoldai.model.ToolCall;
import com.scaffoldai.model.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 原生协议适配器（/api/chat，NDJSON 流式）。默认连接本机 http://localhost:11434。
 */
public class OllamaChatModel implements ChatModel {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OllamaChatModel.class);

    private final String logicalName;
    private final String providerName;
    private final ProviderConfig config;
    private final AiHttpClient http;

    public OllamaChatModel(String logicalName, String providerName, ProviderConfig config, AiHttpClient http) {
        this.logicalName = logicalName;
        this.providerName = providerName;
        this.config = config;
        this.http = http;
    }

    @Override
    public String name() {
        return logicalName;
    }

    @Override
    public String provider() {
        return providerName;
    }

    @Override
    public String modelName() {
        return TextUtils.defaultIfBlank(config.chatModel(), "qwen2.5:7b");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String url = AiHttpClient.requireUrl(config.baseUrl(), "/api/chat", logicalName);
        if (log.isDebugEnabled()) {
            log.debug("ollama 请求 POST {} 逻辑名={} 真实模型={} 消息数={}",
                    url, logicalName, modelOf(request), request.messages().size());
        }
        String raw = http.postJson(url, headers(), buildBody(request, false), timeout());
        JsonNode root = JsonUtils.readTree(raw);
        if (root.has("error")) {
            throw new ModelCallException(root.path("error").asText("Ollama 返回错误"));
        }
        JsonNode message = root.path("message");
        String content = message.path("content").asText("");
        List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
        TokenUsage usage = new TokenUsage(root.path("prompt_eval_count").asInt(0), root.path("eval_count").asInt(0));
        if (usage.totalTokens() == 0) {
            usage = TokenUsage.estimate(joinMessages(request.messages()), content);
        }
        return new ChatResponse(content, toolCalls, usage, modelOf(request), root.path("done_reason").asText("stop"));
    }

    @Override
    public void stream(ChatRequest request, ChatStreamSink sink) {
        String url = AiHttpClient.requireUrl(config.baseUrl(), "/api/chat", logicalName);
        sink.onStart();
        StringBuilder collected = new StringBuilder();
        TokenUsage[] holder = new TokenUsage[]{null};
        http.streamJsonLines(url, headers(), buildBody(request, true), Duration.ofSeconds(Math.max(120, config.timeoutSeconds())), line -> {
            if (line == null || line.isBlank()) {
                return true;
            }
            JsonNode node = JsonUtils.readTree(line);
            if (node.has("error")) {
                throw new ModelCallException(node.path("error").asText("Ollama 流式返回错误"));
            }
            String token = node.path("message").path("content").asText("");
            if (!token.isEmpty()) {
                collected.append(token);
                sink.onToken(token);
            }
            if (node.path("done").asBoolean(false)) {
                holder[0] = new TokenUsage(node.path("prompt_eval_count").asInt(0), node.path("eval_count").asInt(0));
                return false;
            }
            return true;
        });
        TokenUsage usage = holder[0] != null && holder[0].totalTokens() > 0
                ? holder[0]
                : TokenUsage.estimate(joinMessages(request.messages()), collected.toString());
        sink.onUsage(usage);
        sink.onFinish();
    }

    private Map<String, Object> buildBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelOf(request));
        body.put("messages", toWireMessages(request.messages()));
        body.put("stream", stream);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", request.temperature() != null ? request.temperature() : config.temperature());
        Integer maxTokens = request.maxTokens() != null ? request.maxTokens() : config.maxTokens();
        if (maxTokens != null && maxTokens > 0) {
            options.put("num_predict", maxTokens);
        }
        body.put("options", options);
        if (request.hasTools()) {
            body.put("tools", toWireTools(request.tools()));
        }
        return body;
    }

    private List<Map<String, Object>> toWireMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role().wireName());
            if (message.role() == Role.ASSISTANT && message.hasToolCalls()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall call : message.toolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", call.name());
                    fn.put("arguments", JsonUtils.readTree(call.arguments()));
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("function", fn);
                    calls.add(c);
                }
                item.put("tool_calls", calls);
                item.put("content", message.content());
            } else if (message.role() == Role.TOOL) {
                item.put("role", "tool");
                item.put("content", message.content());
                if (TextUtils.hasText(message.name())) {
                    item.put("tool_name", message.name());
                }
            } else {
                item.put("content", message.content());
            }
            wire.add(item);
        }
        return wire;
    }

    private List<Map<String, Object>> toWireTools(List<ToolSpec> tools) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ToolSpec tool : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.put("parameters", tool.parameters());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function");
            item.put("function", fn);
            wire.add(item);
        }
        return wire;
    }

    private List<ToolCall> parseToolCalls(JsonNode node) {
        List<ToolCall> calls = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return calls;
        }
        int index = 0;
        for (JsonNode item : node) {
            String name = item.path("function").path("name").asText("");
            JsonNode args = item.path("function").path("arguments");
            String arguments = args.isTextual() ? args.asText() : JsonUtils.toJson(args);
            String id = item.path("id").asText("call_" + index++);
            if (TextUtils.hasText(name)) {
                calls.add(new ToolCall(id, name, arguments));
            }
        }
        return calls;
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

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (TextUtils.hasText(config.apiKey())) {
            headers.put("Authorization", "Bearer " + config.apiKey().trim());
        }
        return headers;
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds());
    }
}
