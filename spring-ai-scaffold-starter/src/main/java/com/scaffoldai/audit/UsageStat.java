package com.scaffoldai.audit;

/**
 * 按天 + 模型聚合的用量统计，用于计费与运营看板。
 */
public record UsageStat(String day, String model, long requests, long promptTokens, long completionTokens) {

    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
