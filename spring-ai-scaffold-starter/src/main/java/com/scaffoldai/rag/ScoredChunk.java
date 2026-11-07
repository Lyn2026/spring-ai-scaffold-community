package com.scaffoldai.rag;

/**
 * 召回结果。score 为归一化后的相关度（0~1）。
 */
public record ScoredChunk(String chunkId, String docId, String docName, int chunkIndex,
                          String content, double score) {

    public ScoredChunk withScore(double newScore) {
        return new ScoredChunk(chunkId, docId, docName, chunkIndex, content, newScore);
    }
}
