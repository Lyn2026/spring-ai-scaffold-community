package com.scaffoldai.moderation;

/**
 * 审核器 SPI。可叠加多个实现（关键词、正则、调用云审核 API、LLM 判别）。
 */
public interface Moderator {

    String name();

    ModerationResult check(String text);
}
