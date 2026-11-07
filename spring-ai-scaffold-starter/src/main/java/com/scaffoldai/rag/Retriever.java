package com.scaffoldai.rag;

import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.EmbeddingModel;
import com.scaffoldai.model.ModelRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索器：向量召回 + 关键词召回（混合）+ 可选重排，输出可直接拼进提示词的上下文与引用。
 */
public class Retriever {

    private final KnowledgeStore store;
    private final ModelRegistry models;
    private final Reranker reranker;
    private final int topK;
    private final double minScore;
    private final boolean hybrid;
    private final boolean rerankEnabled;

    public Retriever(KnowledgeStore store, ModelRegistry models, Reranker reranker,
                     int topK, double minScore, boolean hybrid, boolean rerankEnabled) {
        this.store = store;
        this.models = models;
        this.reranker = reranker;
        this.topK = Math.max(1, topK);
        this.minScore = Math.max(0.0, minScore);
        this.hybrid = hybrid;
        this.rerankEnabled = rerankEnabled;
    }

    public RagContext retrieve(String tenantId, List<String> kbIds, String question, String embeddingModelName) {
        if (kbIds == null || kbIds.isEmpty() || !TextUtils.hasText(question)) {
            return RagContext.empty();
        }
        EmbeddingModel embeddingModel = models.embeddingModel(embeddingModelName);
        float[] queryVector = embeddingModel.embed(question);

        List<ScoredChunk> candidates = new ArrayList<>();
        for (String kbId : kbIds) {
            if (store.getKb(tenantId, kbId) == null) {
                continue;
            }
            candidates.addAll(retrieveOne(tenantId, kbId, question, queryVector));
        }
        if (candidates.isEmpty()) {
            return RagContext.empty();
        }
        candidates.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        int candidateLimit = Math.min(candidates.size(), Math.max(topK * 3, topK));
        List<ScoredChunk> shortlist = candidates.size() <= candidateLimit ? candidates : candidates.subList(0, candidateLimit);

        List<ScoredChunk> finalList;
        if (rerankEnabled) {
            finalList = reranker.rerank(question, shortlist, topK);
        } else {
            finalList = shortlist.size() <= topK ? shortlist : shortlist.subList(0, topK);
        }
        return buildContext(finalList);
    }

    private List<ScoredChunk> retrieveOne(String tenantId, String kbId, String question, float[] queryVector) {
        List<ScoredChunk> vectorHits = store.search(tenantId, kbId, queryVector, topK * 3, minScore);
        if (!hybrid) {
            return vectorHits;
        }
        Map<String, ScoredChunk> merged = new LinkedHashMap<>();
        for (ScoredChunk hit : vectorHits) {
            merged.put(hit.chunkId(), hit);
        }
        for (ScoredChunk hit : store.keywordSearch(tenantId, kbId, question, topK * 2)) {
            double keywordScore = hit.score() * 0.85;
            ScoredChunk existing = merged.get(hit.chunkId());
            if (existing == null) {
                merged.put(hit.chunkId(), hit.withScore(keywordScore));
            } else {
                merged.put(hit.chunkId(), existing.withScore(Math.max(existing.score(), keywordScore)));
            }
        }
        return new ArrayList<>(merged.values());
    }

    private RagContext buildContext(List<ScoredChunk> chunks) {
        if (chunks.isEmpty()) {
            return RagContext.empty();
        }
        StringBuilder sb = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        int index = 0;
        for (ScoredChunk chunk : chunks) {
            index++;
            if (index > topK) {
                break;
            }
            String snippet = TextUtils.truncate(chunk.content(), 900);
            sb.append("【资料 ").append(index).append("】")
                    .append("来源：").append(chunk.docName())
                    .append("（片段 #").append(chunk.chunkIndex())
                    .append("，相关度 ").append(String.format("%.2f", chunk.score())).append("）\n")
                    .append(snippet).append("\n\n");
            citations.add(new Citation(index, chunk.chunkId(), chunk.docId(), chunk.docName(),
                    chunk.chunkIndex(), TextUtils.truncate(chunk.content(), 200), chunk.score()));
        }
        return new RagContext(sb.toString().trim(), citations, citations.size());
    }
}
