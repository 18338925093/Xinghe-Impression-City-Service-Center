package com.xinghe.trade.config;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "xinghe.messaging.enabled", havingValue = "true")
public class MessagingConfig {
    public static final String ORDER_EXCHANGE = "xinghe.order.exchange";
    public static final String ORDER_TIMEOUT_QUEUE = "xinghe.order.timeout";
    public static final String ORDER_DLX = "xinghe.order.dlx";
    @Bean DirectExchange orderExchange() { return new DirectExchange(ORDER_EXCHANGE); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(ORDER_DLX); }
    @Bean Queue orderTimeoutQueue() { return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE).deadLetterExchange(ORDER_DLX).deadLetterRoutingKey("order.cancel").build(); }
    @Bean Queue orderCancelQueue() { return QueueBuilder.durable("xinghe.order.cancel").build(); }
    @Bean Binding timeoutBinding() { return BindingBuilder.bind(orderTimeoutQueue()).to(orderExchange()).with("order.timeout"); }
    @Bean Binding cancelBinding() { return BindingBuilder.bind(orderCancelQueue()).to(deadLetterExchange()).with("order.cancel"); }
}
