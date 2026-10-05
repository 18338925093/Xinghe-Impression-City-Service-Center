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
    // 订单超时消息先进入延迟队列，消息过期后由死信交换机转发取消事件。
    @Bean DirectExchange orderExchange() { return new DirectExchange(ORDER_EXCHANGE); }
    // 死信交换机负责将过期订单路由到取消队列。
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(ORDER_DLX); }
    // 持久化超时队列并配置过期消息的死信路由。
    @Bean Queue orderTimeoutQueue() { return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE).deadLetterExchange(ORDER_DLX).deadLetterRoutingKey("order.cancel").build(); }
    // 取消队列由 OrderTimeoutConsumer 消费并执行状态变更与库存补偿。
    @Bean Queue orderCancelQueue() { return QueueBuilder.durable("xinghe.order.cancel").build(); }
    // 将订单超时路由键绑定到延迟队列。
    @Bean Binding timeoutBinding() { return BindingBuilder.bind(orderTimeoutQueue()).to(orderExchange()).with("order.timeout"); }
    // 将死信路由键绑定到订单取消队列。
    @Bean Binding cancelBinding() { return BindingBuilder.bind(orderCancelQueue()).to(deadLetterExchange()).with("order.cancel"); }
}
