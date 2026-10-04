package com.xinghe.trade.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xinghe.trade.logistics.LogisticsQueryService;
import com.xinghe.trade.product.Product;
import com.xinghe.trade.product.ProductQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerAgentServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queriesOrderWithServerUserAndReturnsAgentAnswer() throws Exception {
        CustomerAgentProperties properties = enabledProperties();
        CustomerAgentClient client = mock(CustomerAgentClient.class);
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        LogisticsQueryService logisticsQueryService = mock(LogisticsQueryService.class);
        ProductQueryService productQueryService = mock(ProductQueryService.class);
        when(orderQueryService.query("user-1", "XH12345678"))
                .thenReturn(new OrderQueryService.OrderView("XH12345678", "user-1", 9L, 2,
                        new BigDecimal("12.50"), "PAID", "2026-10-03T12:00:00"));
        when(client.complete(any(ObjectNode.class))).thenReturn(
                objectMapper.readTree("""
                        {"choices":[{"message":{"role":"assistant","tool_calls":[{"id":"call-1","type":"function","function":{"name":"query_order","arguments":"{\\"orderNo\\":\\"XH12345678\\",\\"userId\\":\\"user-2\\"}"}}]}}]}
                        """),
                objectMapper.readTree("""
                        {"choices":[{"message":{"role":"assistant","content":"订单已支付。"}}]}
                        """));

        CustomerAgentService service = new CustomerAgentService(properties, client, orderQueryService,
                logisticsQueryService, productQueryService, objectMapper);
        var answer = service.reply("user-1", List.of("USER: 请查一下订单 XH12345678"));

        assertThat(answer).contains("订单已支付。");
        verify(orderQueryService).query("user-1", "XH12345678");
        verifyNoInteractions(logisticsQueryService, productQueryService);

        ArgumentCaptor<ObjectNode> requests = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client, times(2)).complete(requests.capture());
        JsonNode secondRequestMessages = requests.getAllValues().get(1).path("messages");
        JsonNode toolResult = objectMapper.readTree(secondRequestMessages.get(3).path("content").asText());
        assertThat(secondRequestMessages.get(3).path("role").asText()).isEqualTo("tool");
        assertThat(toolResult.path("status").asText()).isEqualTo("PAID");
        assertThat(toolResult.has("userId")).isFalse();
    }

    @Test
    void doesNotCallProviderWhenAgentIsDisabled() {
        CustomerAgentProperties properties = new CustomerAgentProperties();
        CustomerAgentClient client = mock(CustomerAgentClient.class);
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        LogisticsQueryService logisticsQueryService = mock(LogisticsQueryService.class);
        ProductQueryService productQueryService = mock(ProductQueryService.class);
        CustomerAgentService service = new CustomerAgentService(properties, client, orderQueryService,
                logisticsQueryService, productQueryService, objectMapper);

        assertThat(service.reply("user-1", List.of("USER: 你好"))).isEmpty();
        verifyNoInteractions(client, orderQueryService, logisticsQueryService, productQueryService);
    }

    @Test
    void queriesLogisticsUsingCurrentUser() throws Exception {
        CustomerAgentProperties properties = enabledProperties();
        CustomerAgentClient client = mock(CustomerAgentClient.class);
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        LogisticsQueryService logisticsQueryService = mock(LogisticsQueryService.class);
        ProductQueryService productQueryService = mock(ProductQueryService.class);
        when(logisticsQueryService.query("user-1", "XH12345678"))
                .thenReturn(new LogisticsQueryService.LogisticsView("XH12345678", "星河速运", "TRACK-1",
                        "IN_TRANSIT", "包裹已到达配送站", null));
        when(client.complete(any(ObjectNode.class))).thenReturn(
                toolCall("query_logistics", objectMapper.createObjectNode().put("orderNo", "XH12345678")),
                objectMapper.readTree("""
                        {"choices":[{"message":{"role":"assistant","content":"物流运输中。"}}]}
                        """));
        CustomerAgentService service = new CustomerAgentService(properties, client, orderQueryService,
                logisticsQueryService, productQueryService, objectMapper);

        assertThat(service.reply("user-1", List.of("USER: 查一下物流 XH12345678"))).contains("物流运输中。");
        verify(logisticsQueryService).query("user-1", "XH12345678");
        verifyNoInteractions(orderQueryService, productQueryService);
    }

    @Test
    void searchesProductsAndLimitsToolOutput() throws Exception {
        CustomerAgentProperties properties = enabledProperties();
        CustomerAgentClient client = mock(CustomerAgentClient.class);
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        LogisticsQueryService logisticsQueryService = mock(LogisticsQueryService.class);
        ProductQueryService productQueryService = mock(ProductQueryService.class);
        List<Product> products = java.util.stream.LongStream.rangeClosed(1, 10).mapToObj(id -> {
            Product product = new Product();
            product.setId(id);
            product.setStoreId(3L);
            product.setName("运动鞋 " + id);
            product.setDescription("适合日常穿着");
            product.setPrice(new BigDecimal("99.00"));
            product.setStatus("ON_SALE");
            return product;
        }).toList();
        when(productQueryService.search("运动鞋")).thenReturn(products);
        when(client.complete(any(ObjectNode.class))).thenReturn(
                toolCall("search_products", objectMapper.createObjectNode().put("keyword", "运动鞋")),
                objectMapper.readTree("""
                        {"choices":[{"message":{"role":"assistant","content":"找到相关商品。"}}]}
                        """));
        CustomerAgentService service = new CustomerAgentService(properties, client, orderQueryService,
                logisticsQueryService, productQueryService, objectMapper);

        assertThat(service.reply("user-1", List.of("USER: 搜索运动鞋"))).contains("找到相关商品。");
        verify(productQueryService).search("运动鞋");
        ArgumentCaptor<ObjectNode> requests = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client, times(2)).complete(requests.capture());
        JsonNode messages = requests.getAllValues().get(1).path("messages");
        JsonNode toolResult = objectMapper.readTree(messages.get(3).path("content").asText());
        assertThat(toolResult.path("products")).hasSize(8);
        verifyNoInteractions(orderQueryService, logisticsQueryService);
    }

    @Test
    void rejectsUnknownToolAndFallsBackWithoutExecutingBusinessServices() throws Exception {
        CustomerAgentProperties properties = enabledProperties();
        CustomerAgentClient client = mock(CustomerAgentClient.class);
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        LogisticsQueryService logisticsQueryService = mock(LogisticsQueryService.class);
        ProductQueryService productQueryService = mock(ProductQueryService.class);
        when(client.complete(any(ObjectNode.class))).thenReturn(objectMapper.readTree("""
                {"choices":[{"message":{"role":"assistant","tool_calls":[{"id":"call-1","type":"function","function":{"name":"cancel_order","arguments":"{}"}}]}}]}
                """));
        CustomerAgentService service = new CustomerAgentService(properties, client, orderQueryService,
                logisticsQueryService, productQueryService, objectMapper);

        assertThat(service.reply("user-1", List.of("USER: 取消订单"))).isEmpty();
        verifyNoInteractions(orderQueryService, logisticsQueryService, productQueryService);
    }

    private CustomerAgentProperties enabledProperties() {
        CustomerAgentProperties properties = new CustomerAgentProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        return properties;
    }

    private JsonNode toolCall(String name, JsonNode arguments) throws Exception {
        ObjectNode completion = objectMapper.createObjectNode();
        ObjectNode message = completion.putArray("choices").addObject().putObject("message");
        message.put("role", "assistant");
        ObjectNode call = message.putArray("tool_calls").addObject();
        call.put("id", "call-1");
        call.put("type", "function");
        ObjectNode function = call.putObject("function");
        function.put("name", name);
        function.put("arguments", objectMapper.writeValueAsString(arguments));
        return completion;
    }
}
