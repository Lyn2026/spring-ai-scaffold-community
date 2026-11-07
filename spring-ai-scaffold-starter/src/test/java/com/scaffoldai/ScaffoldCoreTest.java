package com.scaffoldai;

import com.scaffoldai.common.Ids;
import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.prompt.PromptRegistry;
import com.scaffoldai.rag.TextSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心纯函数单测：不依赖 Spring 上下文与外部模型，保证基础件行为稳定。
 */
class ScaffoldCoreTest {

    @Test
    void textSplitterKeepsChunksWithinSize() {
        TextSplitter splitter = new TextSplitter(200, 20);
        String paragraph = "这是第一句。这是第二句！这是第三句？这是第四句；这是第五句。";
        String text = String.join("", List.of(paragraph, paragraph, paragraph, paragraph, paragraph));

        List<String> chunks = splitter.split(text);

        assertFalse(chunks.isEmpty(), "长文本应产生至少一个分片");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 220, "分片长度不应超过 chunkSize + overlap：" + chunk.length());
            assertFalse(chunk.isBlank(), "分片不应为空");
        }
        assertTrue(splitter.split("").isEmpty(), "空文本应返回空列表");
    }

    @Test
    void promptTemplateRendersAndClearsMissingPlaceholders() {
        String template = "问题：{{question}}\n上下文：{{context}}\n未定义：{{unknown}}";

        String rendered = PromptRegistry.renderTemplate(template, Map.of("question", "年假几天"));

        assertTrue(rendered.contains("年假几天"));
        assertFalse(rendered.contains("{{question}}"));
        assertFalse(rendered.contains("{{unknown}}"), "未填充的占位符应被清空");
        assertEquals("", PromptRegistry.renderTemplate(null, Map.of()));
    }

    @Test
    void textUtilsHandlesCjkTokensAndTruncation() {
        assertEquals(4, TextUtils.estimateTokens("年假制度"));
        assertEquals(0, TextUtils.estimateTokens(""));
        assertEquals("abc…", TextUtils.truncate("abcdef", 3));
        assertEquals("abc", TextUtils.truncate("abc", 3));
        assertEquals("fallback", TextUtils.defaultIfBlank("  ", "fallback"));

        double coverage = TextUtils.keywordCoverage("年假", "员工入职满一年享受带薪年假");
        assertTrue(coverage > 0.5, "关键词应命中：" + coverage);
        assertEquals(0.0, TextUtils.keywordCoverage("", "任意文档"));
    }

    @Test
    void jsonAndIdsBehaveAsDocumented() {
        Map<String, Object> map = JsonUtils.fromMap("{\"a\":1,\"b\":\"x\"}");
        assertEquals(1, map.get("a"));
        assertEquals("x", map.get("b"));

        String id = Ids.shortId();
        assertEquals(12, id.length());
        assertTrue(id.matches("[0-9a-f]{12}"));
    }
}
