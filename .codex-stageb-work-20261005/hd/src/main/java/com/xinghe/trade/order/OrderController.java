package com.xinghe.trade.order;

import com.xinghe.trade.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }
    @PostMapping
    public ApiResponse<OrderService.OrderResult> create(@RequestHeader("X-User-Id") String userId,
                                                        @Valid @RequestBody CreateOrderRequest request) {
        // 创建订单前校验请求头用户与请求体用户一致，避免冒用其他用户下单。
        requireSameUser(userId, request.userId());
        return ApiResponse.ok(service.create(request));
    }
    @PostMapping("/{orderNo}/pay")
    public ApiResponse<OrderService.OrderResult> pay(@PathVariable String orderNo,
                                                     @RequestHeader("X-User-Id") String userId,
                                                     @RequestHeader("Idempotency-Token") String token) {
        // 使用幂等 Token 发起支付，重复请求由服务层返回安全结果。
        return ApiResponse.ok(service.pay(orderNo, requireUser(userId), token));
    }

    // 阶段 A：开发联调期间校验请求头与请求体用户一致，避免订单接口被冒用。
    private void requireSameUser(String headerUserId, String bodyUserId) {
        if (!requireUser(headerUserId).equals(bodyUserId == null ? "" : bodyUserId.trim())) {
            throw new SecurityException("请求用户与订单用户不一致");
        }
    }

    private String requireUser(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("X-User-Id 请求头不能为空");
        return userId.trim();
    }
    public record CreateOrderRequest(@NotBlank String userId, @NotNull Long skuId, @NotNull Integer quantity, @NotNull BigDecimal amount) {}
}
