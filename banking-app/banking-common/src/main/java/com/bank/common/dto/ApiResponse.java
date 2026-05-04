package com.bank.common.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		boolean success,
		int status,
		String message,
		T data,
		List<ApiError> errors,
		String requestId,
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	    LocalDateTime timestamp
	) {
	
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ApiError(
			String field,
			String code,
			String message
	) {
		public static ApiError of(String code, String message) {
			return new ApiError(null,code,message);
		}
		public static ApiError field(String field, String code, String message) {
			return new ApiError(field,code,message);
		}
	}
	
	public static <T> ApiResponse<T> ok(String message, T data) {
		return build(true, 200, message, data, null);
	}
    public static <T> ApiResponse<T> ok(String message) {
        return build(true, 200, message, null, null);
    }
    public static <T> ApiResponse<T> created(String message, T data) {
        return build(true, 201, message, data, null);
    }
    public static <T> ApiResponse<T> noContent() {
        return build(true, 204, null, null, null);
    }
    public static <T> ApiResponse<T> error(int status, String message, List<ApiError> errors) {
        return build(false, status, message, null, errors);
    }
    public static <T> ApiResponse<T> error(int status, String message) {
        return build(false, status, message, null, null);
    }
    public static <T> ApiResponse<T> badRequest(String message, List<ApiError> errors) {
        return build(false, 400, message, null, errors);
    }
    public static <T> ApiResponse<T> unauthorized(String message) {
        return build(false, 401, message, null, null);
    }
    public static <T> ApiResponse<T> forbidden(String message) {
        return build(false, 403, message, null, null);
    }
    public static <T> ApiResponse<T> notFound(String message) {
        return build(false, 404, message, null, null);
    }
    public static <T> ApiResponse<T> conflict(String message) {
        return build(false, 409, message, null, null);
    }
    public static <T> ApiResponse<T> unprocessable(String message, List<ApiError> errors) {
        return build(false, 422, message, null, errors);
    }
    public static <T> ApiResponse<T> internalError(String message) {
        return build(false, 500, message, null, null);
    }
    private static <T> ApiResponse<T> build(
            boolean success, int status, String message,
            T data, List<ApiError> errors) {
 
        return new ApiResponse<>(
            success,
            status,
            message,
            data,
            errors,
            UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            LocalDateTime.now()
        );
    }
    public boolean isSuccess() {
        return success;
    }
 
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
	
	
}
