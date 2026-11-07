package com.scaffoldai.rag;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;
import com.scaffoldai.common.Ids;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.model.EmbeddingModel;
import com.scaffoldai.model.ModelRegistry;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务：建库、上传文档、切分、向量化、入库、删除。
 */
public class KnowledgeBaseService {

    private final KnowledgeStore store;
    private final DocumentParserFactory parserFactory;
    private final TextSplitter splitter;
    private final ModelRegistry models;
    private final int embeddingBatchSize;
    private final long maxFileSize;

    public KnowledgeBaseService(KnowledgeStore store, DocumentParserFactory parserFactory, TextSplitter splitter,
                                ModelRegistry models, int embeddingBatchSize, long maxFileSize) {
        this.store = store;
        this.parserFactory = parserFactory;
        this.splitter = splitter;
        this.models = models;
        this.embeddingBatchSize = Math.max(1, embeddingBatchSize);
        this.maxFileSize = maxFileSize <= 0 ? 10L * 1024 * 1024 : maxFileSize;
    }

    public KbInfo createKb(String tenantId, String name, String description, String embeddingModelName) {
        if (!TextUtils.hasText(name)) {
            throw AiException.invalid("知识库名称不能为空");
        }
        String modelName = TextUtils.hasText(embeddingModelName)
                ? embeddingModelName
                : models.defaultEmbeddingModel().name();
        EmbeddingModel embeddingModel = models.embeddingModel(modelName);
        String kbId = "kb_" + Ids.shortId();
        KbInfo info = new KbInfo(kbId, tenantId, name.trim(), TextUtils.defaultIfBlank(description, ""),
                embeddingModel.name(), embeddingModel.dimension(), Instant.now());
        store.createKb(info);
        return info;
    }

    public List<KbInfo> listKbs(String tenantId) {
        return store.listKbs(tenantId);
    }

    public KbInfo requireKb(String tenantId, String kbId) {
        KbInfo info = store.getKb(tenantId, kbId);
        if (info == null) {
            throw AiException.notFound("知识库不存在或不属于当前租户: " + kbId);
        }
        return info;
    }

    public void deleteKb(String tenantId, String kbId) {
        requireKb(tenantId, kbId);
        store.deleteKb(tenantId, kbId);
    }

    /**
     * 上传并索引文档。失败时写入 FAILED 记录并抛出异常，便于前端提示。
     */
    public DocInfo ingest(String tenantId, String kbId, String filename, String contentType, byte[] bytes) {
        KbInfo kb = requireKb(tenantId, kbId);
        if (bytes == null || bytes.length == 0) {
            throw AiException.invalid("文件内容为空");
        }
        if (bytes.length > maxFileSize) {
            throw new AiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "文件超过大小限制: " + (maxFileSize / 1024 / 1024) + "MB");
        }
        DocInfo pending = DocInfo.pending("doc_" + Ids.shortId(), tenantId, kbId,
                TextUtils.defaultIfBlank(filename, "untitled"), TextUtils.defaultIfBlank(contentType, "text/plain"),
                bytes.length);
        store.saveDocument(pending);
        try {
            String text = parserFactory.resolve(filename).parse(new ByteArrayInputStream(bytes));
            List<String> pieces = splitter.split(text);
            if (pieces.isEmpty()) {
                throw AiException.invalid("文档解析后无有效文本内容");
            }
            List<ChunkRecord> records = vectorize(kb, pending.id(), pieces);
            store.saveChunks(tenantId, kbId, records);
            DocInfo indexed = pending.indexed(records.size());
            store.saveDocument(indexed);
            return indexed;
        } catch (RuntimeException e) {
            DocInfo failed = pending.failed(TextUtils.truncate(e.getMessage(), 500));
            try {
                store.saveDocument(failed);
            } catch (RuntimeException ignore) {
                // 状态回写失败不影响主错误抛出
            }
            if (e instanceof AiException aiException) {
                throw aiException;
            }
            throw new AiException(ErrorCode.INTERNAL_ERROR, "文档入库失败: " + e.getMessage(), e);
        }
    }

    public List<DocInfo> listDocuments(String tenantId, String kbId) {
        requireKb(tenantId, kbId);
        return store.listDocuments(tenantId, kbId);
    }

    public DocInfo getDocument(String tenantId, String kbId, String docId) {
        DocInfo info = store.getDocument(tenantId, kbId, docId);
        if (info == null) {
            throw AiException.notFound("文档不存在: " + docId);
        }
        return info;
    }

    public void deleteDocument(String tenantId, String kbId, String docId) {
        requireKb(tenantId, kbId);
        store.deleteDocument(tenantId, kbId, docId);
    }

    public long countChunks(String tenantId, String kbId) {
        return store.countChunks(tenantId, kbId);
    }

    private List<ChunkRecord> vectorize(KbInfo kb, String docId, List<String> pieces) {
        EmbeddingModel embeddingModel = models.embeddingModel(kb.embeddingModel());
        List<ChunkRecord> records = new ArrayList<>(pieces.size());
        for (int start = 0; start < pieces.size(); start += embeddingBatchSize) {
            int end = Math.min(pieces.size(), start + embeddingBatchSize);
            List<String> batch = pieces.subList(start, end);
            List<float[]> vectors = embeddingModel.embed(batch);
            for (int i = 0; i < batch.size(); i++) {
                float[] vector = vectors.get(i);
                if (vector == null || vector.length != kb.dimension()) {
                    throw new AiException(ErrorCode.INVALID_ARGUMENT,
                            "向量维度与知识库不一致：知识库 " + kb.dimension() + "，实际 "
                                    + (vector == null ? 0 : vector.length) + "，请用相同向量模型重建知识库");
                }
                records.add(new ChunkRecord("ck_" + Ids.shortId(), kb.tenantId(), kb.id(), docId,
                        start + i, batch.get(i), vector, null));
            }
        }
        return records;
    }
}
