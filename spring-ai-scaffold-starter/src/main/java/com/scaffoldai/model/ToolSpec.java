package com.scaffoldai.model;

import java.util.Map;

/**
 * 暴露给模型的工具描述（JSON Schema）。
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {

    public ToolSpec {
        parameters = parameters == null ? Map.of("type", "object", "properties", Map.of()) : parameters;
    }
}
