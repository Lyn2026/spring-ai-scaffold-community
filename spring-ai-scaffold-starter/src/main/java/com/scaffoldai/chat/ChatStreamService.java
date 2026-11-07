package com.scaffoldai.chat;

import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.ChatMessage;
import com.scaffoldai.model.ChatRequest;
import com.scaffoldai.model.ChatStreamSink;
import com.scaffoldai.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式对话（SSE）：事件序列 meta → token* → done / error。
 * 说明：流式链路暂不启用工具调用，需要工具时请使用 POST /api/ai/chat 非流式接口。
 */
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private final ChatService chatService;
    private final ExecutorService executor;

    public ChatStreamService(ChatService chatService, ExecutorService executor) {
        this.chatService = chatService;
        this.executor = executor;
    }

    public void stream(ChatCommand command, SseEmitter emitter) {
        AtomicBoolean finished = new AtomicBoolean(false);
        emitter.onTimeout(() -> {
            if (finished.compareAndSet(false, true)) {
                emitter.complete();
            }
        });
        emitter.onError(ex -> log.debug("SSE 连接异常: {}", ex.getMessage()));
        emitter.onCompletion(() -> finished.set(true));
        executor.execute(() -> run(command, emitter, finished));
    }

    private void run(ChatCommand command, SseEmitter emitter, AtomicBoolean finished) {
        long started = System.currentTimeMillis();
        ChatPreparation prep = null;
        StringBuilder collected = new StringBuilder();
        TokenUsage usage = TokenUsage.ZERO;
        try {
            prep = chatService.prepare(command);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("requestId", prep.requestId());
            meta.put("conversationId", prep.conversationId());
            meta.put("model", prep.modelName());
            meta.put("citations", prep.ragContext().citations());
            meta.put("tools", List.of());
            send(emitter, "meta", meta);

            TokenUsage[] usageHolder = new TokenUsage[]{TokenUsage.ZERO};
            if (log.isDebugEnabled()) {
                log.debug("流式调用 -> 逻辑名={} provider={} 真实模型={} 消息数={} temperature={} maxTokens={}",
                        prep.modelName(), prep.model().provider(), prep.model().modelName(),
                        prep.messages().size(), prep.temperature(), prep.maxTokens());
            }
            prep.model().stream(new ChatRequest(prep.model().modelName(), prep.messages(),
                    prep.temperature(), prep.maxTokens(), List.of(), Map.of()), new ChatStreamSink() {
                @Override
                public void onToken(String token) {
                    collected.append(token);
                    try {
                        send(emitter, "token", Map.of("content", token));
                    } catch (IOException e) {
                        throw new IllegalStateException("SSE 推送失败", e);
                    }
                }

                @Override
                public void onUsage(TokenUsage tokenUsage) {
                    usageHolder[0] = tokenUsage;
                }
            });
            usage = usageHolder[0];

            String answer = collected.toString();
            long latency = System.currentTimeMillis() - started;
            chatService.complete(prep, answer, usage, latency);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("conversationId", prep.conversationId());
            done.put("answer", answer);
            done.put("promptTokens", usage.promptTokens());
            done.put("completionTokens", usage.completionTokens());
            done.put("latencyMs", latency);
            send(emitter, "done", done);
            if (finished.compareAndSet(false, true)) {
                emitter.complete();
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("流式对话失败: {}", message);
            if (prep != null) {
                chatService.auditFailure(prep, collected.toString(), usage,
                        System.currentTimeMillis() - started, com.scaffoldai.audit.AuditRecord.STATUS_FAILED,
                        TextUtils.truncate(message, 200));
            }
            try {
                send(emitter, "error", Map.of("message", TextUtils.truncate(message, 500)));
            } catch (IOException ignore) {
                // 客户端可能已断开
            }
            if (finished.compareAndSet(false, true)) {
                // 已经通过 error 事件告知客户端失败，这里正常结束，避免异步异常二次派发
                emitter.complete();
            }
        }
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(JsonUtils.toJson(data), MediaType.APPLICATION_JSON));
    }
}
