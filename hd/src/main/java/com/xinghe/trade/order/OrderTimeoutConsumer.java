package com.xinghe.trade.order;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "xinghe.messaging.enabled", havingValue = "true")
public class OrderTimeoutConsumer {
    private final RedisTemplate<String, Object> redis;
    public OrderTimeoutConsumer(RedisTemplate<String, Object> redis) { this.redis = redis; }
    @RabbitListener(queues = "xinghe.order.cancel")
    public void cancel(String orderNo) {
        Object status = redis.opsForHash().get("order:" + orderNo, "status");
        if ("PENDING_PAYMENT".equals(status)) redis.opsForHash().put("order:" + orderNo, "status", "CANCELLED");
    }
}
