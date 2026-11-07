package com.scaffoldai.model;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;

/**
 * 模型调用异常。retryable 用于决定是否重试或切到降级模型（429/5xx/超时可重试）。
 */
public class ModelCallException extends AiException {

    private final int statusCode;
    private final boolean retryable;

    public ModelCallException(String message) {
        this(0, message, false, null);
    }

    public ModelCallException(String message, Throwable cause) {
        this(0, message, true, cause);
    }

    public ModelCallException(int statusCode, String message, boolean retryable, Throwable cause) {
        super(ErrorCode.MODEL_ERROR, message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public static ModelCallException httpError(int status, String url, String body) {
        boolean retryable = status == 408 || status == 409 || status == 429 || status >= 500;
        String message = "模型调用失败 status=" + status + " url=" + url + " body=" + abbreviate(body);
        return new ModelCallException(status, message, retryable, null);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }
}
