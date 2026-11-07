package com.scaffoldai.rag;

import java.util.List;

/**
 * 重排 SPI。默认实现返回原顺序；开启后可用 LLM 或专用 rerank 模型提升准确率。
 */
public interface Reranker {

    String name();

    List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK);
}
