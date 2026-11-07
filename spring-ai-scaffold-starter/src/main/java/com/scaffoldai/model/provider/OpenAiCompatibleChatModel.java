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
 * OpenAI 兼容协议适配器：DashScope 兼容模式、DeepSeek、vLLM、OneAPI 等均可复用。
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiCompatibleChatModel.class);

    private final String logicalName;
    private final String providerName;
    private final ProviderConfig config;
    private final AiHttpClient http;

    public OpenAiCompatibleChatModel(String logicalName, String providerName, ProviderConfig config, AiHttpClient http) {
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
        return TextUtils.defaultIfBlank(config.chatModel(), "qwen-plus");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String url = AiHttpClient.requireUrl(config.baseUrl(), "/chat/completions", logicalName);
        if (log.isDebugEnabled()) {
            log.debug("openai-compatible 请求 POST {} 逻辑名={} 真实模型={} 消息数={} 工具数={}",
                    url, logicalName, modelOf(request), request.messages().size(), request.tools().size());
        }
        String raw = http.postJson(url, headers(), buildBody(request, false), timeout());
        JsonNode root = JsonUtils.readTree(raw);
        if (root.has("error")) {
            throw new ModelCallException(root.path("error").path("message").asText("模型返回错误"));
        }
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").asText("");
        List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
        TokenUsage usage = parseUsage(root.path("usage"), request.messages(), content);
        if (log.isDebugEnabled()) {
            log.debug("openai-compatible 响应 真实模型={} finishReason={} 内容长度={} 工具调用={} promptTokens={} completionTokens={}",
                    modelOf(request), choice.path("finish_reason").asText("stop"), content.length(),
                    toolCalls.size(), usage.promptTokens(), usage.completionTokens());
        }
        return new ChatResponse(content, toolCalls, usage, modelOf(request), choice.path("finish_reason").asText("stop"));
    }

    @Override
    public void stream(ChatRequest request, ChatStreamSink sink) {
        String url = AiHttpClient.requireUrl(config.baseUrl(), "/chat/completions", logicalName);
        if (log.isDebugEnabled()) {
            log.debug("openai-compatible 流式请求 POST {} 逻辑名={} 真实模型={} 消息数={}",
                    url, logicalName, modelOf(request), request.messages().size());
        }
        sink.onStart();
        StringBuilder collected = new StringBuilder();
        TokenUsage[] usageHolder = new TokenUsage[]{null};
        http.streamJsonLines(url, headers(), buildBody(request, true), Duration.ofSeconds(Math.max(120, config.timeoutSeconds())), line -> {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
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
            if (node.has("error")) {
                throw new ModelCallException(node.path("error").path("message").asText("模型流式返回错误"));
            }
            JsonNode delta = node.path("choices").path(0).path("delta");
            String token = delta.path("content").asText("");
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
        body.put("messages", toWireMessages(request.messages()));
        Double temperature = request.temperature() != null ? request.temperature() : config.temperature();
        body.put("temperature", temperature);
        Integer maxTokens = request.maxTokens() != null ? request.maxTokens() : config.maxTokens();
        if (maxTokens != null && maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        if (stream) {
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
        }
        if (request.hasTools()) {
            body.put("tools", toWireTools(request.tools()));
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private List<Map<String, Object>> toWireMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role().wireName());
            if (message.role() == Role.TOOL) {
                item.put("tool_call_id", message.toolCallId() == null ? "call_unknown" : message.toolCallId());
                if (TextUtils.hasText(message.name())) {
                    item.put("name", message.name());
                }
                item.put("content", message.content());
            } else if (message.role() == Role.ASSISTANT && message.hasToolCalls()) {
                if (TextUtils.hasText(message.content())) {
                    item.put("content", message.content());
                }
                item.put("tool_calls", toWireToolCalls(message.toolCalls()));
            } else {
                item.put("content", message.content());
            }
            wire.add(item);
        }
        return wire;
    }

    private List<Map<String, Object>> toWireToolCalls(List<ToolCall> calls) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ToolCall call : calls) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", call.name());
            fn.put("arguments", call.arguments());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", call.id());
            item.put("type", "function");
            item.put("function", fn);
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
            String id = item.path("id").asText("");
            if (!TextUtils.hasText(id)) {
                id = "call_" + index++;
            }
            String name = item.path("function").path("name").asText("");
            JsonNode args = item.path("function").path("arguments");
            String arguments = args.isTextual() ? args.asText() : JsonUtils.toJson(args);
            if (TextUtils.hasText(name)) {
                calls.add(new ToolCall(id, name, arguments));
            }
        }
        return calls;
    }

    private TokenUsage parseUsage(JsonNode usage, List<ChatMessage> messages, String completion) {
        int prompt = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
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
