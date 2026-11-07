package com.scaffoldai.common;

import java.util.UUID;

/**
 * ID 生成工具。会话、文档、分片、请求链路 ID 统一走这里，方便日志串联。
 */
public final class Ids {

    private Ids() {
    }

    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String shortId() {
        return next().substring(0, 12);
    }

    public static String requestId() {
        return "req_" + shortId();
    }
}
