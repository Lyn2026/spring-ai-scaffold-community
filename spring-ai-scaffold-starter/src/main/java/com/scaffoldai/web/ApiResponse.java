package com.scaffoldai.web;

import com.scaffoldai.common.ErrorCode;

/**
 * 统一响应包装：{ok, code, message, data}。
 */
public record ApiResponse<T>(boolean ok, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.name(), message, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
