package com.xinghe.trade.order;

import com.xinghe.trade.product.Product;
import com.xinghe.trade.product.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    @Test
    void rejectsClientTamperedAmountBeforeReservingStock() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrderItemMapper itemMapper = mock(TradeOrderItemMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        Product product = product("ON_SALE", "199.00");
        when(productMapper.selectById(10001L)).thenReturn(product);
        OrderService service = new OrderService(redis, rabbit, orderMapper, itemMapper, productMapper);

        // 阶段 A：服务端价格重算必须拒绝客户端篡改订单金额。
        assertThatThrownBy(() -> service.create(new OrderController.CreateOrderRequest(
                "user-1", 10001L, 2, new BigDecimal("1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("订单金额与商品价格不一致");
        verifyNoInteractions(orderMapper, itemMapper, redis);
    }

    @Test
    void rejectsOffSaleProductBeforeReservingStock() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        when(productMapper.selectById(10001L)).thenReturn(product("OFF_SALE", "199.00"));
        OrderService service = new OrderService(redis, rabbit, mock(TradeOrderMapper.class),
                mock(TradeOrderItemMapper.class), productMapper);

        // 阶段 A：下单前校验商品在售状态，避免下架商品继续扣减库存。
        assertThatThrownBy(() -> service.create(new OrderController.CreateOrderRequest(
                "user-1", 10001L, 1, new BigDecimal("199.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已下架");
        verifyNoInteractions(redis);
    }

    @Test
    void rejectsPaymentFromAnotherUserBeforeTakingIdempotencyToken() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrder order = new TradeOrder();
        order.setOrderNo("XH12345678");
        order.setUserId("user-1");
        order.setStatus("PENDING_PAYMENT");
        when(orderMapper.selectById("XH12345678")).thenReturn(order);
        OrderService service = new OrderService(redis, rabbit, orderMapper,
                mock(TradeOrderItemMapper.class), mock(ProductMapper.class));

        // 阶段 A：支付前校验订单归属，越权请求不能占用幂等 Token。
        assertThatThrownBy(() -> service.pay("XH12345678", "user-2", "token-1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("无权支付该订单");
        verifyNoInteractions(redis);
    }

    @Test
    void doesNotReportPaymentSuccessWhenTimeoutCancellationWinsRace() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("pay:idempotent:token-1"), eq("XH12345678"), any())).thenReturn(true);
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrder pending = new TradeOrder();
        pending.setOrderNo("XH12345678");
        pending.setUserId("user-1");
        pending.setStatus("PENDING_PAYMENT");
        TradeOrder cancelled = new TradeOrder();
        cancelled.setOrderNo("XH12345678");
        cancelled.setUserId("user-1");
        cancelled.setStatus("CANCELLED");
        when(orderMapper.selectById("XH12345678")).thenReturn(pending, cancelled);
        when(orderMapper.update(any(TradeOrder.class), any())).thenReturn(0);
        OrderService service = new OrderService(redis, mock(RabbitTemplate.class), orderMapper,
                mock(TradeOrderItemMapper.class), mock(ProductMapper.class));

        // 阶段 A：订单超时取消先完成时，支付条件更新失败并明确返回冲突，不得误报支付成功。
        assertThatThrownBy(() -> service.pay("XH12345678", "user-1", "token-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("订单已取消，支付失败");
        verify(redis).delete("pay:idempotent:token-1");
    }

    private Product product(String status, String price) {
        Product product = new Product();
        product.setId(10001L);
        product.setPrice(new BigDecimal(price));
        product.setStatus(status);
        return product;
    }
}
