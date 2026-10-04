package com.xinghe.trade.order;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderTimeoutConsumerTest {
    @Test
    void restoresReservedStockOnlyWhenPendingOrderIsCancelled() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrder order = pendingOrder();
        when(orderMapper.selectById("XH12345678")).thenReturn(order);
        when(orderMapper.update(any(TradeOrder.class), any())).thenReturn(1);

        OrderTimeoutConsumer consumer = new OrderTimeoutConsumer(redis, orderMapper);
        consumer.cancel("XH12345678");

        // 阶段 A：验证库存恢复通过 Redis Lua 原子脚本执行，并写入订单级幂等标记。
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of("stock:sku:9", "stock:restored:XH12345678")), eq("2"), eq("2592000"));
    }

    @Test
    void doesNotRestoreStockWhenTimeoutMessageIsDuplicated() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrder order = pendingOrder();
        when(orderMapper.selectById("XH12345678")).thenReturn(order);
        when(orderMapper.update(any(TradeOrder.class), any())).thenReturn(1, 0);

        OrderTimeoutConsumer consumer = new OrderTimeoutConsumer(redis, orderMapper);
        consumer.cancel("XH12345678");
        consumer.cancel("XH12345678");

        // 阶段 A：重复超时消息无法再次完成状态迁移，因此不应重复发起库存恢复。
        verify(redis, times(1)).execute(any(DefaultRedisScript.class), anyList(), any(), any());
    }

    @Test
    void retriesStockRestoreWhenDatabaseCancellationAlreadyWon() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrder pending = pendingOrder();
        TradeOrder cancelled = pendingOrder();
        cancelled.setStatus("CANCELLED");
        when(orderMapper.selectById("XH12345678")).thenReturn(pending, cancelled);
        when(orderMapper.update(any(TradeOrder.class), any())).thenReturn(1, 0);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("模拟 Redis 暂不可用"))
                .thenReturn(1L);

        OrderTimeoutConsumer consumer = new OrderTimeoutConsumer(redis, orderMapper);
        // 阶段 A：模拟首次 Redis 补偿失败，确认异常向 RabbitMQ 监听容器传播以触发重试。
        assertThatThrownBy(() -> consumer.cancel("XH12345678"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟 Redis 暂不可用");
        consumer.cancel("XH12345678");

        // 阶段 A：数据库取消成功后若首次 Redis 操作失败，重投消息仍可再次执行幂等脚本补偿。
        verify(redis, times(2)).execute(any(DefaultRedisScript.class), anyList(), any(), any());
    }

    private TradeOrder pendingOrder() {
        TradeOrder order = new TradeOrder();
        order.setOrderNo("XH12345678");
        order.setSkuId(9L);
        order.setQuantity(2);
        order.setStatus("PENDING_PAYMENT");
        return order;
    }
}
