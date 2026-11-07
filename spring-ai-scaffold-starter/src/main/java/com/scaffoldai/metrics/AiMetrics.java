package com.scaffoldai.metrics;

import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

/**
 * 指标埋点：请求量、token 消耗、时延、工具调用。
 * 未引入 micrometer 时自动降级为 no-op，不强制宿主应用安装 Actuator。
 */
public class AiMetrics {

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public boolean enabled() {
        return registry != null;
    }

    public void chatRequest(String model, String status) {
        if (registry == null) {
            return;
        }
        registry.counter("ai.chat.requests", "model", safe(model), "status", safe(status)).increment();
    }

    public void tokens(String model, int promptTokens, int completionTokens) {
        if (registry == null) {
            return;
        }
        registry.counter("ai.chat.tokens", "model", safe(model), "type", "prompt").increment(promptTokens);
        registry.counter("ai.chat.tokens", "model", safe(model), "type", "completion").increment(completionTokens);
    }

    public void chatLatency(String model, long millis) {
        if (registry == null) {
            return;
        }
        registry.timer("ai.chat.latency", "model", safe(model)).record(Duration.ofMillis(Math.max(0, millis)));
    }

    public void ragLatency(long millis) {
        if (registry == null) {
            return;
        }
        registry.timer("ai.rag.latency").record(Duration.ofMillis(Math.max(0, millis)));
    }

    public void toolCall(String toolName, String status) {
        if (registry == null) {
            return;
        }
        registry.counter("ai.tool.calls", "tool", safe(toolName), "status", safe(status)).increment();
    }

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }
}
