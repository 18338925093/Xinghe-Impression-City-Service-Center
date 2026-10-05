package com.xinghe.trade.order;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "xinghe.messaging.enabled", havingValue = "true")
public class OrderTimeoutConsumer {
    private static final DefaultRedisScript<Long> RESTORE_STOCK_SCRIPT = new DefaultRedisScript<>("""
            local stock = KEYS[1]
            local marker = KEYS[2]
            if redis.call('EXISTS', marker) == 1 then return 0 end
            redis.call('INCRBY', stock, ARGV[1])
            redis.call('SET', marker, '1', 'EX', ARGV[2])
            return 1
            """, Long.class);
    private final RedisTemplate<String, Object> redis;
    private final TradeOrderMapper orderMapper;

    public OrderTimeoutConsumer(RedisTemplate<String, Object> redis, TradeOrderMapper orderMapper) {
        this.redis = redis;
        this.orderMapper = orderMapper;
    }

    @RabbitListener(queues = "xinghe.order.cancel")
    public void cancel(String orderNo) {
        // 消费订单超时消息，仅取消仍处于待支付状态的订单并恢复库存。
        TradeOrder order = orderMapper.selectById(orderNo);
        if (order == null) return;
        TradeOrder update = new TradeOrder();
        update.setOrderNo(orderNo);
        update.setStatus("CANCELLED");
        update.setUpdatedAt(LocalDateTime.now());
        int updated = orderMapper.update(update, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getStatus, "PENDING_PAYMENT"));
        // 阶段 A：若数据库已取消但 Redis 恢复失败，RabbitMQ 重投时继续依靠 Lua 幂等标记补偿库存。
        if (updated == 1 || "CANCELLED".equals(order.getStatus())) restoreStockOnce(order);
    }

    // 阶段 A：用 Lua 将库存恢复和幂等标记放入同一个 Redis 原子操作，支持失败重试。
    private void restoreStockOnce(TradeOrder order) {
        // 使用订单级幂等标记，确保消息重复投递不会重复增加库存。
        String stockKey = "stock:sku:" + order.getSkuId();
        String markerKey = "stock:restored:" + order.getOrderNo();
        redis.execute(RESTORE_STOCK_SCRIPT, List.of(stockKey, markerKey),
                String.valueOf(order.getQuantity()), "2592000");
    }
}
