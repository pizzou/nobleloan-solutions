package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import lombok.Data;

@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }

    /**
     * Safe response boundary for legacy endpoints. Entity values are converted
     * to detached DTOs before Jackson ever sees them.
     */
    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> safe(T data) {
        return ok((T) ResponseDtoMapper.safe(data));
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> safe(String message, T data) {
        return ok(message, (T) ResponseDtoMapper.safe(data));
    }

    public static <T> ApiResponse<T> safe(String message) {
        return ok(message);
    }
}
