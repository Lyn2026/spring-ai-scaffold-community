package com.scaffoldai.chat;

import java.util.List;
import java.util.Map;

/**
 * 一次对话请求的内部命令对象（与 HTTP DTO 解耦，Agent/定时任务也可复用）。
 */
public record ChatCommand(
        String tenantId,
        String userId,
        String conversationId,
        String question,
        String model,
        List<String> kbIds,
        boolean useRag,
        boolean useTools,
        Double temperature,
        Integer maxTokens,
        String systemPrompt,
        Map<String, String> metadata
) {

    public ChatCommand {
        kbIds = kbIds == null ? List.of() : List.copyOf(kbIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ChatCommand simple(String tenantId, String userId, String question) {
        return builder().tenantId(tenantId).userId(userId).question(question).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tenantId;
        private String userId;
        private String conversationId;
        private String question;
        private String model;
        private List<String> kbIds = List.of();
        private boolean useRag = true;
        private boolean useTools = true;
        private Double temperature;
        private Integer maxTokens;
        private String systemPrompt;
        private Map<String, String> metadata = Map.of();

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder kbIds(List<String> kbIds) {
            this.kbIds = kbIds == null ? List.of() : kbIds;
            return this;
        }

        public Builder useRag(boolean useRag) {
            this.useRag = useRag;
            return this;
        }

        public Builder useTools(boolean useTools) {
            this.useTools = useTools;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? Map.of() : metadata;
            return this;
        }

        public ChatCommand build() {
            return new ChatCommand(tenantId, userId, conversationId, question, model, kbIds,
                    useRag, useTools, temperature, maxTokens, systemPrompt, metadata);
        }
    }
}
