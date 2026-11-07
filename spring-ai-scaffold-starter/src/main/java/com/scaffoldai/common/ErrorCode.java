package com.scaffoldai.common;

/**
 * 统一错误码。返回给调用方时同时用于 HTTP 状态码映射。
 */
public enum ErrorCode {

    INVALID_ARGUMENT(400, "参数不合法"),
    UNAUTHORIZED(401, "未认证或凭证无效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    PAYLOAD_TOO_LARGE(413, "内容超出限制"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的文件类型"),
    RATE_LIMITED(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务内部错误"),
    MODEL_ERROR(502, "模型调用失败"),
    MODERATION_BLOCKED(422, "内容未通过审核"),
    STORE_ERROR(500, "存储访问失败");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
