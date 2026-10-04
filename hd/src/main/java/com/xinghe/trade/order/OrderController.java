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
    public ApiResponse<OrderService.OrderResult> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(service.create(request));
    }
    @PostMapping("/{orderNo}/pay")
    public ApiResponse<OrderService.OrderResult> pay(@PathVariable String orderNo, @RequestHeader("Idempotency-Token") String token) {
        return ApiResponse.ok(service.pay(orderNo, token));
    }
    public record CreateOrderRequest(@NotBlank String userId, @NotNull Long skuId, @NotNull Integer quantity, @NotNull BigDecimal amount) {}
}
