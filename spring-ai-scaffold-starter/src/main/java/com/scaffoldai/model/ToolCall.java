package com.scaffoldai.model;

/**
 * 一次工具调用请求（由模型发起）。
 *
 * @param id        调用 ID，OpenAI 协议要求回传 tool_call_id；Ollama 不返回时由适配层补
 * @param name      工具名
 * @param arguments 参数 JSON 字符串
 */
public record ToolCall(String id, String name, String arguments) {

    public ToolCall {
        arguments = arguments == null || arguments.isBlank() ? "{}" : arguments;
    }
}
