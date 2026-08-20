package com.ineb.kms.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 모든 REST API의 공통 응답 포맷: { success, data, message, errorCode } */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, String errorCode) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static ApiResponse<Void> error(String message, String errorCode) {
        return new ApiResponse<>(false, null, message, errorCode);
    }
}
