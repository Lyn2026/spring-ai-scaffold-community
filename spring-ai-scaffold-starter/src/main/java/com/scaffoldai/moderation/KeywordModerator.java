package com.scaffoldai.moderation;

import com.scaffoldai.common.TextUtils;

import java.util.List;
import java.util.Locale;

/**
 * 关键词审核：命中配置的敏感词即拦截。适合内部知识库/客服场景的底线防护。
 * 生产环境建议再叠加云厂商内容安全 API 或 LLM 判别。
 */
public class KeywordModerator implements Moderator {

    private final List<String> blockedWords;

    public KeywordModerator(List<String> blockedWords) {
        this.blockedWords = blockedWords == null ? List.of() : blockedWords.stream()
                .filter(TextUtils::hasText)
                .map(word -> word.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    @Override
    public String name() {
        return "keyword";
    }

    @Override
    public ModerationResult check(String text) {
        if (blockedWords.isEmpty() || !TextUtils.hasText(text)) {
            return ModerationResult.ok();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : blockedWords) {
            if (lower.contains(word)) {
                return ModerationResult.blocked("keyword", "内容包含敏感词: " + word);
            }
        }
        return ModerationResult.ok();
    }
}
