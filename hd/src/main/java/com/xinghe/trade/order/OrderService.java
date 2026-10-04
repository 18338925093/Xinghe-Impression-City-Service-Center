package com.xinghe.trade.order;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.xinghe.trade.config.MessagingConfig.ORDER_EXCHANGE;

@Service
public class OrderService {
    private final RedisTemplate<String, Object> redis;
    private final RabbitTemplate rabbit;

    public OrderService(RedisTemplate<String, Object> redis, RabbitTemplate rabbit) {
        this.redis = redis;
        this.rabbit = rabbit;
    }

    public OrderResult create(OrderController.CreateOrderRequest request) {
        String orderNo = "XH" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String stockKey = "stock:sku:" + request.skuId();
        Long remaining = redis.opsForValue().decrement(stockKey, request.quantity());
        if (remaining != null && remaining < 0) {
            redis.opsForValue().increment(stockKey, request.quantity());
            throw new IllegalStateException("库存不足");
        }

        String orderKey = "order:" + orderNo;
        redis.opsForHash().put(orderKey, "userId", request.userId());
        redis.opsForHash().put(orderKey, "skuId", String.valueOf(request.skuId()));
        redis.opsForHash().put(orderKey, "quantity", String.valueOf(request.quantity()));
        redis.opsForHash().put(orderKey, "amount", request.amount().toPlainString());
        redis.opsForHash().put(orderKey, "status", "PENDING_PAYMENT");
        redis.opsForHash().put(orderKey, "createdAt", OffsetDateTime.now().toString());
        rabbit.convertAndSend(ORDER_EXCHANGE, "order.timeout", orderNo, delayed(30_000));
        return new OrderResult(orderNo, "PENDING_PAYMENT");
    }

    public OrderResult pay(String orderNo, String token) {
        String key = "pay:idempotent:" + token;
        Boolean first = redis.opsForValue().setIfAbsent(key, orderNo, Duration.ofMinutes(15));
        if (Boolean.FALSE.equals(first)) return new OrderResult(orderNo, "PAID (重复请求已幂等返回)");
        redis.opsForHash().put("order:" + orderNo, "status", "PAID");
        return new OrderResult(orderNo, "PAID");
    }

    private MessagePostProcessor delayed(long millis) {
        return message -> {
            message.getMessageProperties().setExpiration(String.valueOf(millis));
            return message;
        };
    }

    public record OrderResult(String orderNo, String status) {}
}
