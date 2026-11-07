package com.scaffoldai.rag;

import java.util.Map;

/**
 * 分片记录：文本 + 向量 + 轻量元数据。
 */
public record ChunkRecord(String id, String tenantId, String kbId, String docId,
                          int chunkIndex, String content, float[] vector, Map<String, String> metadata) {

    public ChunkRecord {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
