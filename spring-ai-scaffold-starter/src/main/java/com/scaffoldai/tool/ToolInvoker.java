package com.scaffoldai.tool;

import java.util.Map;

/**
 * 工具执行器抽象：本地反射调用与远程 MCP 工具共用同一套注册/调用链路。
 */
@FunctionalInterface
public interface ToolInvoker {

    /**
     * @param arguments 已解析的参数 Map
     * @return 字符串结果，直接回传给模型
     */
    String invoke(Map<String, Object> arguments);
}
