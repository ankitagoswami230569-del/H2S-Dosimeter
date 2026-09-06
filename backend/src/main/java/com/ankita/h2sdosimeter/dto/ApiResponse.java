package com.ankita.h2sdosimeter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic API response envelope.
 * All endpoints return this wrapper to give the client consistent
 * success/error information alongside the data payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String  message;
    private T       data;
    private String  error;

    // --- Factory helpers ---

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data    = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        r.data    = data;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error   = message;
        return r;
    }

    // Getters / Setters
    public boolean isSuccess()      { return success; }
    public void setSuccess(boolean v){ this.success = v; }
    public String getMessage()      { return message; }
    public void setMessage(String v){ this.message = v; }
    public T getData()              { return data; }
    public void setData(T v)        { this.data = v; }
    public String getError()        { return error; }
    public void setError(String v)  { this.error = v; }
}
