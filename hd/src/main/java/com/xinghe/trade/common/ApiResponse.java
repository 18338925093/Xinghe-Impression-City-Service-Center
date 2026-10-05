package com.xinghe.trade.common;

public record ApiResponse<T>(int code, String message, T data) {
    // 统一封装成功响应，便于前端按固定结构处理接口结果。
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, "success", data); }
    // 统一封装业务失败响应，避免各个控制器重复定义返回格式。
    public static <T> ApiResponse<T> fail(String message) { return new ApiResponse<>(-1, message, null); }
}
