package com.scaffoldai.rag;

import java.time.Instant;

/**
 * 文档元信息。status: PENDING / INDEXED / FAILED。
 */
public record DocInfo(String id, String tenantId, String kbId, String filename, String contentType,
                      long size, int chunkCount, String status, String error, Instant createdAt) {

    public static DocInfo pending(String id, String tenantId, String kbId, String filename, String contentType, long size) {
        return new DocInfo(id, tenantId, kbId, filename, contentType, size, 0, "PENDING", null, Instant.now());
    }

    public DocInfo indexed(int chunks) {
        return new DocInfo(id, tenantId, kbId, filename, contentType, size, chunks, "INDEXED", null, createdAt);
    }

    public DocInfo failed(String message) {
        return new DocInfo(id, tenantId, kbId, filename, contentType, size, 0, "FAILED", message, createdAt);
    }
}
