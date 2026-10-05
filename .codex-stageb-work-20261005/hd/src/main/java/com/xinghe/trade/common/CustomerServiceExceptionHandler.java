package com.xinghe.trade.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CustomerServiceExceptionHandler {
    // 阶段 A：订单状态冲突统一返回 409，便于前端区分业务失败和系统故障。
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    // 处理订单状态冲突、支付处理中等需要客户端重试或提示的业务异常。
    public ApiResponse<Void> conflict(IllegalStateException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    // 处理参数缺失、商品不存在和金额不匹配等请求错误。
    public ApiResponse<Void> badRequest(IllegalArgumentException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    // 处理跨用户查询、支付或会话访问，防止接口直接返回服务器错误。
    public ApiResponse<Void> forbidden(SecurityException exception) {
        return ApiResponse.fail(exception.getMessage());
    }
}
