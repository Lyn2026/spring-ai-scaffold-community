package com.scaffoldai.web;

/**
 * 知识库检索请求体。
 */
public record SearchRequest(String query, Integer topK) {
}
