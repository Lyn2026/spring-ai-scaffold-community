package com.scaffoldai.rag;

import java.util.List;

/**
 * 知识库存储 SPI：元数据 + 分片 + 向量检索。
 * 生产推荐 pgvector（带 HNSW 索引），小体量/无 PG 时可用 redis 或 memory。
 */
public interface KnowledgeStore {

    String type();

    void createKb(KbInfo info);

    List<KbInfo> listKbs(String tenantId);

    KbInfo getKb(String tenantId, String kbId);

    void deleteKb(String tenantId, String kbId);

    void saveDocument(DocInfo info);

    List<DocInfo> listDocuments(String tenantId, String kbId);

    DocInfo getDocument(String tenantId, String kbId, String docId);

    void deleteDocument(String tenantId, String kbId, String docId);

    void saveChunks(String tenantId, String kbId, List<ChunkRecord> chunks);

    /**
     * 向量召回。
     */
    List<ScoredChunk> search(String tenantId, String kbId, float[] queryVector, int topK, double minScore);

    /**
     * 关键词召回，用于混合检索。实现可返回空列表表示不支持。
     */
    default List<ScoredChunk> keywordSearch(String tenantId, String kbId, String query, int topK) {
        return List.of();
    }

    long countChunks(String tenantId, String kbId);
}
