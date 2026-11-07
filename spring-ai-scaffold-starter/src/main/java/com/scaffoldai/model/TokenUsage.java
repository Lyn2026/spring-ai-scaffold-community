package com.scaffoldai.model;

import com.scaffoldai.common.TextUtils;

/**
 * token 用量。未拿到模型返回值时使用估算值，保证计费与统计链路不断。
 */
public record TokenUsage(int promptTokens, int completionTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0);

    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
    }

    public static TokenUsage estimate(String prompt, String completion) {
        return new TokenUsage(TextUtils.estimateTokens(prompt), TextUtils.estimateTokens(completion));
    }
}
