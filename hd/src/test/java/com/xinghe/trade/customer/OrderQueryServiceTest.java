package com.xinghe.trade.customer;

import org.junit.jupiter.api.Test;
import com.xinghe.trade.order.TradeOrder;
import com.xinghe.trade.order.TradeOrderMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderQueryServiceTest {
    @Test
    void rejectsOrderOwnedByAnotherUser() {
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrder order = new TradeOrder();
        order.setOrderNo("XH12345678");
        order.setUserId("user-1");
        order.setStatus("PAID");
        when(orderMapper.selectById("XH12345678")).thenReturn(order);

        OrderQueryService service = new OrderQueryService(orderMapper);
        assertThatThrownBy(() -> service.query("user-2", "XH12345678"))
                .isInstanceOf(SecurityException.class);
    }
}
