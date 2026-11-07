package com.scaffoldai.rag;

import java.time.Instant;

/**
 * 知识库元信息。embeddingModel 记录建库时使用的向量模型，避免后续换模型导致维度错配。
 */
public record KbInfo(String id, String tenantId, String name, String description,
                     String embeddingModel, int dimension, Instant createdAt) {

    public KbInfo withCreatedAt(Instant time) {
        return new KbInfo(id, tenantId, name, description, embeddingModel, dimension, time);
    }
}
