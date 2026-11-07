package com.scaffoldai.common;

/**
 * 脚手架统一异常。所有对外错误都应包装成它，便于全局异常处理与审计。
 */
public class AiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object detail;

    public AiException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public AiException(ErrorCode code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public AiException(ErrorCode code, String message, Object detail, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.detail = detail;
    }

    public ErrorCode code() {
        return code;
    }

    public Object detail() {
        return detail;
    }

    public static AiException invalid(String message) {
        return new AiException(ErrorCode.INVALID_ARGUMENT, message);
    }

    public static AiException notFound(String message) {
        return new AiException(ErrorCode.NOT_FOUND, message);
    }

    public static AiException unauthorized(String message) {
        return new AiException(ErrorCode.UNAUTHORIZED, message);
    }

    public static AiException forbidden(String message) {
        return new AiException(ErrorCode.FORBIDDEN, message);
    }
}
