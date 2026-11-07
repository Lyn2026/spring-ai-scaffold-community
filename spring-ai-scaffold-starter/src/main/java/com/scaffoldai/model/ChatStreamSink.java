package com.scaffoldai.model;

/**
 * 流式回调。上层用 SseEmitter 承接，下游实现只感知 token 与用量。
 */
public interface ChatStreamSink {

    void onToken(String token);

    void onUsage(TokenUsage usage);

    /**
     * 流开始，便于上层尽快 flush 空行避免代理缓冲。
     */
    default void onStart() {
    }

    default void onFinish() {
    }
}
