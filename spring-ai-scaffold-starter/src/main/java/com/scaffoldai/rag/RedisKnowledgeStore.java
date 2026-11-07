package com.scaffoldai.rag;

import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Redis 知识库：无需 RediSearch 模块，向量以 JSON 存储在 Hash 中，
 * 检索时在应用侧做余弦计算。适合 1 万条分片以内的轻量知识库；
 * 更大规模请使用 pgvector 实现。
 */
public class RedisKnowledgeStore implements KnowledgeStore {

    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisKnowledgeStore(StringRedisTemplate redis, String prefix) {
        this.redis = redis;
        this.prefix = TextUtils.defaultIfBlank(prefix, "ai:kb:");
    }

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public void createKb(KbInfo info) {
        redis.opsForValue().set(kbKey(info.tenantId(), info.id()), JsonUtils.toJson(info));
        SetOperations<String, String> sets = redis.opsForSet();
        sets.add(indexKey(info.tenantId()), info.id());
    }

    @Override
    public List<KbInfo> listKbs(String tenantId) {
        SetOperations<String, String> sets = redis.opsForSet();
        java.util.Set<String> ids = sets.members(indexKey(tenantId));
        List<KbInfo> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (String id : ids) {
            KbInfo info = getKb(tenantId, id);
            if (info != null) {
                result.add(info);
            }
        }
        result.sort(Comparator.comparing(KbInfo::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public KbInfo getKb(String tenantId, String kbId) {
        String raw = redis.opsForValue().get(kbKey(tenantId, kbId));
        return raw == null ? null : JsonUtils.fromJson(raw, KbInfo.class);
    }

    @Override
    public void deleteKb(String tenantId, String kbId) {
        redis.delete(kbKey(tenantId, kbId));
        redis.delete(docKey(tenantId, kbId));
        redis.delete(chunkKey(tenantId, kbId));
        redis.opsForSet().remove(indexKey(tenantId), kbId);
    }

    @Override
    public void saveDocument(DocInfo info) {
        redis.opsForHash().put(docKey(info.tenantId(), info.kbId()), info.id(), JsonUtils.toJson(info));
    }

    @Override
    public List<DocInfo> listDocuments(String tenantId, String kbId) {
        HashOperations<String, String, String> hash = redis.opsForHash();
        Map<String, String> entries = hash.entries(docKey(tenantId, kbId));
        List<DocInfo> result = new ArrayList<>();
        for (String raw : entries.values()) {
            result.add(JsonUtils.fromJson(raw, DocInfo.class));
        }
        result.sort(Comparator.comparing(DocInfo::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public DocInfo getDocument(String tenantId, String kbId, String docId) {
        String raw = redis.<String, String>opsForHash().get(docKey(tenantId, kbId), docId);
        return raw == null ? null : JsonUtils.fromJson(raw, DocInfo.class);
    }

    @Override
    public void deleteDocument(String tenantId, String kbId, String docId) {
        redis.opsForHash().delete(docKey(tenantId, kbId), docId);
        HashOperations<String, String, String> hash = redis.opsForHash();
        List<String> toRemove = new ArrayList<>();
        Map<String, String> chunks = hash.entries(chunkKey(tenantId, kbId));
        for (Map.Entry<String, String> entry : chunks.entrySet()) {
            ChunkRecord record = JsonUtils.fromJson(entry.getValue(), ChunkRecord.class);
            if (record.docId().equals(docId)) {
                toRemove.add(entry.getKey());
            }
        }
        if (!toRemove.isEmpty()) {
            hash.delete(chunkKey(tenantId, kbId), toRemove.toArray());
        }
    }

    @Override
    public void saveChunks(String tenantId, String kbId, List<ChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        HashOperations<String, String, String> hash = redis.opsForHash();
        for (ChunkRecord chunk : chunks) {
            hash.put(chunkKey(tenantId, kbId), chunk.id(), JsonUtils.toJson(chunk));
        }
    }

    @Override
    public List<ScoredChunk> search(String tenantId, String kbId, float[] queryVector, int topK, double minScore) {
        HashOperations<String, String, String> hash = redis.opsForHash();
        Map<String, String> entries = hash.entries(chunkKey(tenantId, kbId));
        List<ScoredChunk> result = new ArrayList<>();
        Map<String, DocInfo> docCache = new java.util.HashMap<>();
        for (String raw : entries.values()) {
            ChunkRecord record = JsonUtils.fromJson(raw, ChunkRecord.class);
            double score = Math.max(0.0, TextUtils.cosine(queryVector, record.vector()));
            if (score >= minScore) {
                DocInfo doc = docCache.computeIfAbsent(record.docId(), id -> getDocument(tenantId, kbId, id));
                result.add(new ScoredChunk(record.id(), record.docId(),
                        doc == null ? record.docId() : doc.filename(), record.chunkIndex(), record.content(), score));
            }
        }
        result.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return result.size() <= topK ? result : result.subList(0, topK);
    }

    @Override
    public List<ScoredChunk> keywordSearch(String tenantId, String kbId, String query, int topK) {
        HashOperations<String, String, String> hash = redis.opsForHash();
        Map<String, String> entries = hash.entries(chunkKey(tenantId, kbId));
        List<ScoredChunk> result = new ArrayList<>();
        for (String raw : entries.values()) {
            ChunkRecord record = JsonUtils.fromJson(raw, ChunkRecord.class);
            double coverage = TextUtils.keywordCoverage(query, record.content());
            if (coverage > 0.3) {
                DocInfo doc = getDocument(tenantId, kbId, record.docId());
                result.add(new ScoredChunk(record.id(), record.docId(),
                        doc == null ? record.docId() : doc.filename(), record.chunkIndex(), record.content(), coverage));
            }
        }
        result.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return result.size() <= topK ? result : result.subList(0, topK);
    }

    @Override
    public long countChunks(String tenantId, String kbId) {
        Long size = redis.opsForHash().size(chunkKey(tenantId, kbId));
        return size == null ? 0 : size;
    }

    private String kbKey(String tenantId, String kbId) {
        return prefix + tenantId + ":" + kbId;
    }

    private String indexKey(String tenantId) {
        return prefix + "index:" + tenantId;
    }

    private String docKey(String tenantId, String kbId) {
        return prefix + "docs:" + tenantId + ":" + kbId;
    }

    private String chunkKey(String tenantId, String kbId) {
        return prefix + "chunks:" + tenantId + ":" + kbId;
    }
}
