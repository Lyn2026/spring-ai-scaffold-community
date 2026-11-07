package com.scaffoldai.web;

/**
 * 创建知识库请求体。
 */
public record CreateKbRequest(String name, String description, String embeddingModel) {
}
