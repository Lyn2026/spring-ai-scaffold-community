package com.scaffoldai.rag;

import com.scaffoldai.common.TextUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内知识库：无需任何外部依赖，用于 demo 与单机验证。
 * 上限建议 2 万条分片，超过请切换到 pgvector。
 */
public class InMemoryKnowledgeStore implements KnowledgeStore {

    private final Map<String, KbInfo> kbs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, DocInfo>> documents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ChunkRecord>> chunks = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public void createKb(KbInfo info) {
        kbs.put(key(info.tenantId(), info.id()), info);
        documents.putIfAbsent(key(info.tenantId(), info.id()), new ConcurrentHashMap<>());
        chunks.putIfAbsent(key(info.tenantId(), info.id()), new ConcurrentHashMap<>());
    }

    @Override
    public List<KbInfo> listKbs(String tenantId) {
        List<KbInfo> result = new ArrayList<>();
        kbs.forEach((k, v) -> {
            if (v.tenantId().equals(tenantId)) {
                result.add(v);
            }
        });
        return result;
    }

    @Override
    public KbInfo getKb(String tenantId, String kbId) {
        return kbs.get(key(tenantId, kbId));
    }

    @Override
    public void deleteKb(String tenantId, String kbId) {
        String key = key(tenantId, kbId);
        kbs.remove(key);
        documents.remove(key);
        chunks.remove(key);
    }

    @Override
    public void saveDocument(DocInfo info) {
        documents.computeIfAbsent(key(info.tenantId(), info.kbId()), k -> new ConcurrentHashMap<>())
                .put(info.id(), info);
    }

    @Override
    public List<DocInfo> listDocuments(String tenantId, String kbId) {
        Map<String, DocInfo> map = documents.get(key(tenantId, kbId));
        if (map == null) {
            return List.of();
        }
        List<DocInfo> result = new ArrayList<>(map.values());
        result.sort(Comparator.comparing(DocInfo::createdAt).reversed());
        return result;
    }

    @Override
    public DocInfo getDocument(String tenantId, String kbId, String docId) {
        Map<String, DocInfo> map = documents.get(key(tenantId, kbId));
        return map == null ? null : map.get(docId);
    }

    @Override
    public void deleteDocument(String tenantId, String kbId, String docId) {
        Map<String, DocInfo> docMap = documents.get(key(tenantId, kbId));
        if (docMap != null) {
            docMap.remove(docId);
        }
        Map<String, ChunkRecord> chunkMap = chunks.get(key(tenantId, kbId));
        if (chunkMap != null) {
            chunkMap.entrySet().removeIf(entry -> entry.getValue().docId().equals(docId));
        }
    }

    @Override
    public void saveChunks(String tenantId, String kbId, List<ChunkRecord> records) {
        Map<String, ChunkRecord> chunkMap = chunks.computeIfAbsent(key(tenantId, kbId), k -> new ConcurrentHashMap<>());
        for (ChunkRecord record : records) {
            chunkMap.put(record.id(), record);
        }
    }

    @Override
    public List<ScoredChunk> search(String tenantId, String kbId, float[] queryVector, int topK, double minScore) {
        Map<String, ChunkRecord> chunkMap = chunks.get(key(tenantId, kbId));
        if (chunkMap == null || chunkMap.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> result = new ArrayList<>();
        for (ChunkRecord record : chunkMap.values()) {
            double score = Math.max(0.0, TextUtils.cosine(queryVector, record.vector()));
            if (score >= minScore) {
                result.add(toScored(tenantId, kbId, record, score));
            }
        }
        result.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return result.size() <= topK ? result : result.subList(0, topK);
    }

    @Override
    public List<ScoredChunk> keywordSearch(String tenantId, String kbId, String query, int topK) {
        Map<String, ChunkRecord> chunkMap = chunks.get(key(tenantId, kbId));
        if (chunkMap == null || chunkMap.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> result = new ArrayList<>();
        for (ChunkRecord record : chunkMap.values()) {
            double coverage = TextUtils.keywordCoverage(query, record.content());
            if (coverage > 0.3) {
                result.add(toScored(tenantId, kbId, record, coverage));
            }
        }
        result.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return result.size() <= topK ? result : result.subList(0, topK);
    }

    @Override
    public long countChunks(String tenantId, String kbId) {
        Map<String, ChunkRecord> chunkMap = chunks.get(key(tenantId, kbId));
        return chunkMap == null ? 0 : chunkMap.size();
    }

    private ScoredChunk toScored(String tenantId, String kbId, ChunkRecord record, double score) {
        DocInfo doc = getDocument(tenantId, kbId, record.docId());
        String docName = doc == null ? record.docId() : doc.filename();
        return new ScoredChunk(record.id(), record.docId(), docName, record.chunkIndex(), record.content(), score);
    }

    private String key(String tenantId, String kbId) {
        return tenantId + ":" + kbId;
    }
}
