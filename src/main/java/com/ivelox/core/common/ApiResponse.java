package com.ivelox.core.common;

public record ApiResponse<T>(T data, String error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message);
    }
}
