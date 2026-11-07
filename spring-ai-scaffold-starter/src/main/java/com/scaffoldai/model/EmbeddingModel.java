package com.scaffoldai.model;

import java.util.List;

/**
 * 向量模型统一接口。
 */
public interface EmbeddingModel {

    String name();

    /**
     * 向量维度，用于建表与校验。
     */
    int dimension();

    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        List<float[]> vectors = embed(List.of(text == null ? "" : text));
        if (vectors.isEmpty()) {
            throw new ModelCallException("向量模型未返回结果");
        }
        return vectors.get(0);
    }
}
