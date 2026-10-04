package com.xinghe.trade.customer;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class OrderQueryService {
    private final RedisTemplate<String, Object> redis;

    public OrderQueryService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public OrderView query(String userId, String orderNo) {
        Map<Object, Object> values = redis.opsForHash().entries("order:" + orderNo);
        if (values.isEmpty()) throw new IllegalArgumentException("订单不存在");
        String ownerId = text(values.get("userId"));
        if (ownerId == null || !ownerId.equals(userId)) throw new SecurityException("无权查询该订单");
        return new OrderView(
                orderNo,
                ownerId,
                longValue(values.get("skuId")),
                intValue(values.get("quantity")),
                decimalValue(values.get("amount")),
                text(values.get("status")),
                text(values.get("createdAt"))
        );
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Long longValue(Object value) { return value == null ? null : Long.valueOf(text(value)); }
    private Integer intValue(Object value) { return value == null ? null : Integer.valueOf(text(value)); }
    private BigDecimal decimalValue(Object value) { return value == null ? null : new BigDecimal(text(value)); }

    public record OrderView(String orderNo, String userId, Long skuId, Integer quantity,
                            BigDecimal amount, String status, String createdAt) {}
}
