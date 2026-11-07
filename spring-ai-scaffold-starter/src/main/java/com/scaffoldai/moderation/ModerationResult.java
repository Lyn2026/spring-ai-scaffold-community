package com.scaffoldai.moderation;

/**
 * 内容审核结果。
 */
public record ModerationResult(boolean allowed, String reason, String category) {

    public static ModerationResult ok() {
        return new ModerationResult(true, null, "none");
    }

    public static ModerationResult blocked(String category, String reason) {
        return new ModerationResult(false, reason, category);
    }
}
