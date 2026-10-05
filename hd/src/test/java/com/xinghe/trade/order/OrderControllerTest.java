package com.xinghe.trade.order;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderControllerTest {
    @Test
    void normalizesBodyUserIdBeforePassingOrderToService() {
        OrderService service = mock(OrderService.class);
        when(service.create(any())).thenReturn(new OrderService.OrderResult("XH12345678", "PENDING_PAYMENT"));
        OrderController controller = new OrderController(service);

        controller.create("user-1", new OrderController.CreateOrderRequest(
                " user-1 ", 10001L, 1, new BigDecimal("199.00")));

        ArgumentCaptor<OrderController.CreateOrderRequest> request =
                ArgumentCaptor.forClass(OrderController.CreateOrderRequest.class);
        verify(service).create(request.capture());
        assertThat(request.getValue().userId()).isEqualTo("user-1");
        assertThat(request.getValue().skuId()).isEqualTo(10001L);
        assertThat(request.getValue().quantity()).isEqualTo(1);
        assertThat(request.getValue().amount()).isEqualByComparingTo("199.00");
    }
}
