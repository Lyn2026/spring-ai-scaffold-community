package com.scaffoldai.rag;

import java.util.List;

/**
 * 检索上下文：拼装后的提示词片段 + 结构化引用，便于前端做「来源标注」。
 */
public record RagContext(String contextText, List<Citation> citations, int chunkCount) {

    public RagContext {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public static RagContext empty() {
        return new RagContext("", List.of(), 0);
    }

    public boolean isEmpty() {
        return chunkCount == 0 || contextText.isBlank();
    }
}
