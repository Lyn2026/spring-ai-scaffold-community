package com.scaffoldai.tool;

import com.scaffoldai.model.ToolSpec;

import java.util.Map;

/**
 * 已注册的工具定义。
 */
public record ToolDefinition(String name, String description, Map<String, Object> schema, ToolInvoker invoker) {

    public ToolSpec toSpec() {
        return new ToolSpec(name, description, schema);
    }
}
