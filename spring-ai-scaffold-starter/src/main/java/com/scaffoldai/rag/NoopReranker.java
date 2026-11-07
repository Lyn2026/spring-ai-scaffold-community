package com.scaffoldai.rag;

import java.util.List;

/**
 * 不重排，按向量/混合分数截断。吞吐最高，作为默认策略。
 */
public class NoopReranker implements Reranker {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK) {
        return candidates.size() <= topK ? candidates : candidates.subList(0, topK);
    }
}
