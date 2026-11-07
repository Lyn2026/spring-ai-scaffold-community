package com.scaffoldai.moderation;

import com.scaffoldai.common.TextUtils;

import java.util.List;

/**
 * 审核服务：输入长度控制 + 多审核器聚合，命中任一即拦截。
 */
public class ModerationService {

    private final boolean enabled;
    private final int maxInputChars;
    private final List<Moderator> moderators;

    public ModerationService(boolean enabled, int maxInputChars, List<Moderator> moderators) {
        this.enabled = enabled;
        this.maxInputChars = maxInputChars <= 0 ? 8000 : maxInputChars;
        this.moderators = moderators == null ? List.of() : List.copyOf(moderators);
    }

    public ModerationResult check(String text) {
        if (!enabled) {
            return ModerationResult.ok();
        }
        if (text != null && text.length() > maxInputChars) {
            return ModerationResult.blocked("length", "内容长度超过限制 " + maxInputChars + " 字符");
        }
        if (!TextUtils.hasText(text)) {
            return ModerationResult.ok();
        }
        for (Moderator moderator : moderators) {
            ModerationResult result = moderator.check(text);
            if (!result.allowed()) {
                return result;
            }
        }
        return ModerationResult.ok();
    }

    public boolean enabled() {
        return enabled;
    }
}
