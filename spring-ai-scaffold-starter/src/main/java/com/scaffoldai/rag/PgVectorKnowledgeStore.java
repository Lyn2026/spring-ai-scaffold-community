package com.scaffoldai.rag;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;
import com.scaffoldai.common.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL + pgvector 知识库实现：生产默认方案。
 * 依赖：CREATE EXTENSION vector;（首次需超级用户或有权限的账号执行一次）
 */
public class PgVectorKnowledgeStore implements KnowledgeStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorKnowledgeStore.class);

    private final JdbcTemplate jdbc;
    private final int dimension;

    public PgVectorKnowledgeStore(JdbcTemplate jdbc, int dimension, boolean autoInitSchema) {
        this.jdbc = jdbc;
        this.dimension = Math.max(64, dimension);
        if (autoInitSchema) {
            ensureSchema();
        }
    }

    @Override
    public String type() {
        return "pgvector";
    }

    @Override
    public void createKb(KbInfo info) {
        jdbc.update("INSERT INTO ai_kb (id, tenant_id, name, description, embedding_model, dimension, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                info.id(), info.tenantId(), info.name(), info.description(), info.embeddingModel(),
                info.dimension(), Timestamp.from(info.createdAt() == null ? java.time.Instant.now() : info.createdAt()));
    }

    @Override
    public List<KbInfo> listKbs(String tenantId) {
        return jdbc.query("SELECT id, tenant_id, name, description, embedding_model, dimension, created_at "
                        + "FROM ai_kb WHERE tenant_id = ? ORDER BY created_at DESC",
                KB_MAPPER, tenantId);
    }

    @Override
    public KbInfo getKb(String tenantId, String kbId) {
        List<KbInfo> list = jdbc.query("SELECT id, tenant_id, name, description, embedding_model, dimension, created_at "
                + "FROM ai_kb WHERE tenant_id = ? AND id = ?", KB_MAPPER, tenantId, kbId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void deleteKb(String tenantId, String kbId) {
        jdbc.update("DELETE FROM ai_chunk WHERE tenant_id = ? AND kb_id = ?", tenantId, kbId);
        jdbc.update("DELETE FROM ai_document WHERE tenant_id = ? AND kb_id = ?", tenantId, kbId);
        jdbc.update("DELETE FROM ai_kb WHERE tenant_id = ? AND id = ?", tenantId, kbId);
    }

    @Override
    public void saveDocument(DocInfo info) {
        int updated = jdbc.update("UPDATE ai_document SET filename = ?, content_type = ?, size = ?, chunk_count = ?, "
                        + "status = ?, error = ? WHERE id = ?",
                info.filename(), info.contentType(), info.size(), info.chunkCount(), info.status(), info.error(), info.id());
        if (updated > 0) {
            return;
        }
        jdbc.update("INSERT INTO ai_document (id, tenant_id, kb_id, filename, content_type, size, chunk_count, status, error, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                info.id(), info.tenantId(), info.kbId(), info.filename(), info.contentType(), info.size(),
                info.chunkCount(), info.status(), info.error(),
                Timestamp.from(info.createdAt() == null ? java.time.Instant.now() : info.createdAt()));
    }

    @Override
    public List<DocInfo> listDocuments(String tenantId, String kbId) {
        return jdbc.query("SELECT id, tenant_id, kb_id, filename, content_type, size, chunk_count, status, error, created_at "
                        + "FROM ai_document WHERE tenant_id = ? AND kb_id = ? ORDER BY created_at DESC",
                DOC_MAPPER, tenantId, kbId);
    }

    @Override
    public DocInfo getDocument(String tenantId, String kbId, String docId) {
        List<DocInfo> list = jdbc.query("SELECT id, tenant_id, kb_id, filename, content_type, size, chunk_count, status, error, created_at "
                + "FROM ai_document WHERE tenant_id = ? AND kb_id = ? AND id = ?", DOC_MAPPER, tenantId, kbId, docId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void deleteDocument(String tenantId, String kbId, String docId) {
        jdbc.update("DELETE FROM ai_chunk WHERE tenant_id = ? AND kb_id = ? AND doc_id = ?", tenantId, kbId, docId);
        jdbc.update("DELETE FROM ai_document WHERE tenant_id = ? AND kb_id = ? AND id = ?", tenantId, kbId, docId);
    }

    @Override
    public void saveChunks(String tenantId, String kbId, List<ChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>();
        for (ChunkRecord chunk : chunks) {
            if (chunk.vector() == null || chunk.vector().length != dimension) {
                throw new AiException(ErrorCode.INVALID_ARGUMENT,
                        "向量维度不匹配：期望 " + dimension + "，实际 " + (chunk.vector() == null ? 0 : chunk.vector().length)
                                + "，请检查知识库建库时使用的 embedding 模型");
            }
            batch.add(new Object[]{
                    chunk.id(), tenantId, kbId, chunk.docId(), chunk.chunkIndex(),
                    chunk.content(), TextUtils.toPgVector(chunk.vector())
            });
        }
        jdbc.batchUpdate("INSERT INTO ai_chunk (id, tenant_id, kb_id, doc_id, chunk_index, content, embedding) "
                + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector))", batch);
    }

    @Override
    public List<ScoredChunk> search(String tenantId, String kbId, float[] queryVector, int topK, double minScore) {
        if (queryVector == null || queryVector.length != dimension) {
            throw new AiException(ErrorCode.INVALID_ARGUMENT, "查询向量维度不匹配");
        }
        String literal = TextUtils.toPgVector(queryVector);
        List<ScoredChunk> rows = jdbc.query(
                "SELECT c.id, c.doc_id, d.filename, c.chunk_index, c.content, "
                        + "1 - (c.embedding <=> CAST(? AS vector)) AS score "
                        + "FROM ai_chunk c LEFT JOIN ai_document d ON d.id = c.doc_id "
                        + "WHERE c.tenant_id = ? AND c.kb_id = ? "
                        + "ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?",
                CHUNK_MAPPER, literal, tenantId, kbId, literal, Math.max(1, topK));
        List<ScoredChunk> filtered = new ArrayList<>();
        for (ScoredChunk row : rows) {
            if (row.score() >= minScore) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    @Override
    public List<ScoredChunk> keywordSearch(String tenantId, String kbId, String query, int topK) {
        List<String> terms = TextUtils.terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        String best = terms.get(0);
        for (String term : terms) {
            if (term.length() > best.length()) {
                best = term;
            }
        }
        List<ScoredChunk> rows = jdbc.query(
                "SELECT c.id, c.doc_id, d.filename, c.chunk_index, c.content, 0.0 AS score "
                        + "FROM ai_chunk c LEFT JOIN ai_document d ON d.id = c.doc_id "
                        + "WHERE c.tenant_id = ? AND c.kb_id = ? AND c.content LIKE ? LIMIT ?",
                CHUNK_MAPPER, tenantId, kbId, "%" + best + "%", Math.max(topK * 2, topK));
        List<ScoredChunk> scored = new ArrayList<>();
        for (ScoredChunk row : rows) {
            double coverage = TextUtils.keywordCoverage(query, row.content());
            scored.add(row.withScore(coverage));
        }
        return scored;
    }

    @Override
    public long countChunks(String tenantId, String kbId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_chunk WHERE tenant_id = ? AND kb_id = ?",
                Long.class, tenantId, kbId);
        return count == null ? 0 : count;
    }

    private void ensureSchema() {
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (RuntimeException e) {
            log.warn("创建 pgvector 扩展失败（可能缺少权限，请 DBA 手动执行 CREATE EXTENSION vector）: {}", e.getMessage());
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_kb ("
                + "id VARCHAR(64) PRIMARY KEY,"
                + "tenant_id VARCHAR(64) NOT NULL,"
                + "name VARCHAR(200) NOT NULL,"
                + "description VARCHAR(1000),"
                + "embedding_model VARCHAR(128),"
                + "dimension INT NOT NULL,"
                + "created_at TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_document ("
                + "id VARCHAR(64) PRIMARY KEY,"
                + "tenant_id VARCHAR(64) NOT NULL,"
                + "kb_id VARCHAR(64) NOT NULL,"
                + "filename VARCHAR(400),"
                + "content_type VARCHAR(128),"
                + "size BIGINT,"
                + "chunk_count INT,"
                + "status VARCHAR(32),"
                + "error VARCHAR(1000),"
                + "created_at TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_chunk ("
                + "id VARCHAR(64) PRIMARY KEY,"
                + "tenant_id VARCHAR(64) NOT NULL,"
                + "kb_id VARCHAR(64) NOT NULL,"
                + "doc_id VARCHAR(64) NOT NULL,"
                + "chunk_index INT NOT NULL,"
                + "content VARCHAR(100000),"
                + "embedding vector(" + dimension + "))");
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_chunk_kb ON ai_chunk (tenant_id, kb_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_chunk_hnsw ON ai_chunk "
                    + "USING hnsw (embedding vector_cosine_ops)");
        } catch (RuntimeException e) {
            log.warn("创建向量索引失败（pgvector 版本可能低于 0.5，可忽略，检索仍可用但较慢）: {}", e.getMessage());
        }
    }

    private static final RowMapper<KbInfo> KB_MAPPER = (rs, rowNum) -> new KbInfo(
            rs.getString("id"), rs.getString("tenant_id"), rs.getString("name"), rs.getString("description"),
            rs.getString("embedding_model"), rs.getInt("dimension"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<DocInfo> DOC_MAPPER = (rs, rowNum) -> new DocInfo(
            rs.getString("id"), rs.getString("tenant_id"), rs.getString("kb_id"), rs.getString("filename"),
            rs.getString("content_type"), rs.getLong("size"), rs.getInt("chunk_count"), rs.getString("status"),
            rs.getString("error"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<ScoredChunk> CHUNK_MAPPER = (rs, rowNum) -> new ScoredChunk(
            rs.getString("id"), rs.getString("doc_id"), rs.getString("filename"), rs.getInt("chunk_index"),
            rs.getString("content"), Math.max(0.0, rs.getDouble("score")));
}
