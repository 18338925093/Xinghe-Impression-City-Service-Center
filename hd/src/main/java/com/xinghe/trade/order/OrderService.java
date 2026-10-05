package com.xinghe.trade.order;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import com.xinghe.trade.product.Product;
import com.xinghe.trade.product.ProductMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.xinghe.trade.config.MessagingConfig.ORDER_EXCHANGE;

@Service
public class OrderService {
    private final RedisTemplate<String, Object> redis;
    private final RabbitTemplate rabbit;
    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    @Value("${xinghe.messaging.enabled:false}")
    private boolean messagingEnabled;

    public OrderService(RedisTemplate<String, Object> redis, RabbitTemplate rabbit,
                        TradeOrderMapper orderMapper, TradeOrderItemMapper orderItemMapper,
                        ProductMapper productMapper) {
        this.redis = redis;
        this.rabbit = rabbit;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
    }

    @Transactional
    public OrderResult create(OrderController.CreateOrderRequest request) {
        // 校验下单参数、服务端重算金额、扣减 Redis 库存并落库订单明细。
        if (request == null || request.userId() == null || request.userId().isBlank()) throw new IllegalArgumentException("用户 ID 不能为空");
        if (request.skuId() == null) throw new IllegalArgumentException("商品 ID 不能为空");
        if (request.quantity() == null || request.quantity() <= 0) throw new IllegalArgumentException("购买数量必须大于 0");
        if (request.amount() == null || request.amount().signum() < 0) throw new IllegalArgumentException("订单金额不能为负数");

        // 阶段 A：服务端根据在售商品价格重新计算订单金额，避免客户端篡改金额。
        Product product = productMapper.selectById(request.skuId());
        if (product == null || !"ON_SALE".equals(product.getStatus())) {
            throw new IllegalArgumentException("商品不存在或已下架");
        }
        if (product.getPrice() == null || product.getPrice().signum() < 0) {
            throw new IllegalStateException("商品价格配置异常");
        }
        java.math.BigDecimal expectedAmount = product.getPrice()
                .multiply(java.math.BigDecimal.valueOf(request.quantity()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (request.amount().compareTo(expectedAmount) != 0) {
            throw new IllegalArgumentException("订单金额与商品价格不一致");
        }
        String orderNo = "XH" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String stockKey = "stock:sku:" + request.skuId();
        Long remaining = redis.opsForValue().decrement(stockKey, request.quantity());
        if (remaining == null || remaining < 0) {
            redis.opsForValue().increment(stockKey, request.quantity());
            throw new IllegalStateException("库存不足");
        }

        LocalDateTime now = LocalDateTime.now();
        try {
            TradeOrder order = new TradeOrder();
            order.setOrderNo(orderNo);
            order.setUserId(request.userId());
            order.setSkuId(request.skuId());
            order.setQuantity(request.quantity());
            order.setAmount(expectedAmount);
            order.setStatus("PENDING_PAYMENT");
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            // 阶段 A：数据库写入必须确认影响行数，否则抛错触发事务回滚和库存补偿。
            if (orderMapper.insert(order) != 1) throw new IllegalStateException("订单写入失败");

            TradeOrderItem item = new TradeOrderItem();
            item.setOrderNo(orderNo);
            item.setSkuId(request.skuId());
            item.setQuantity(request.quantity());
            item.setUnitPrice(product.getPrice());
            item.setLineAmount(expectedAmount);
            if (orderItemMapper.insert(item) != 1) throw new IllegalStateException("订单明细写入失败");

            if (messagingEnabled) rabbit.convertAndSend(ORDER_EXCHANGE, "order.timeout", orderNo, delayed(30_000));
        } catch (RuntimeException exception) {
            redis.opsForValue().increment(stockKey, request.quantity());
            throw exception;
        }
        return new OrderResult(orderNo, "PENDING_PAYMENT");
    }

    public OrderResult pay(String orderNo, String userId, String token) {
        // 通过 Redis Token 防重并使用条件更新完成支付，和超时取消形成互斥状态流转。
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Idempotency-Token 不能为空");
        TradeOrder order = orderMapper.selectById(orderNo);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (userId == null || userId.isBlank() || !userId.equals(order.getUserId())) {
            throw new SecurityException("无权支付该订单");
        }
        if ("PAID".equals(order.getStatus())) return new OrderResult(orderNo, "PAID");
        if (!"PENDING_PAYMENT".equals(order.getStatus())) throw new IllegalStateException("当前订单不可支付");
        String key = "pay:idempotent:" + token;
        Boolean first = redis.opsForValue().setIfAbsent(key, orderNo, Duration.ofMinutes(15));
        // 阶段 A：Redis 未返回幂等锁结果时立即失败关闭，禁止绕过重复支付保护。
        if (first == null) throw new IllegalStateException("支付幂等服务暂不可用，请稍后重试");
        if (Boolean.FALSE.equals(first)) {
            Object boundOrderNo = redis.opsForValue().get(key);
            if (boundOrderNo != null && !orderNo.equals(String.valueOf(boundOrderNo))) {
                throw new IllegalArgumentException("Idempotency-Token 已被其他订单使用");
            }
            TradeOrder latest = orderMapper.selectById(orderNo);
            if (latest != null && "PAID".equals(latest.getStatus())) {
                return new OrderResult(orderNo, "PAID (重复请求已幂等返回)");
            }
            if (latest != null && "PENDING_PAYMENT".equals(latest.getStatus())) {
                throw new IllegalStateException("支付处理中，请稍后重试");
            }
            throw new IllegalStateException("当前订单不可支付");
        }
        try {
            // 阶段 A：使用订单状态条件更新，和超时取消形成互斥状态迁移。
            TradeOrder update = new TradeOrder();
            update.setOrderNo(orderNo);
            update.setStatus("PAID");
            update.setUpdatedAt(LocalDateTime.now());
            int updated = orderMapper.update(update, new LambdaUpdateWrapper<TradeOrder>()
                    .eq(TradeOrder::getOrderNo, orderNo)
                    .eq(TradeOrder::getStatus, "PENDING_PAYMENT"));
            if (updated == 1) return new OrderResult(orderNo, "PAID");

            // 阶段 A：更新失败后重新读取最终状态，禁止把已取消订单误报为已支付。
            TradeOrder latest = orderMapper.selectById(orderNo);
            if (latest != null && "PAID".equals(latest.getStatus())) return new OrderResult(orderNo, "PAID");
            throw new IllegalStateException("订单已取消，支付失败");
        } catch (RuntimeException exception) {
            // 数据库更新失败时释放本次抢到的幂等 Token，允许客户端安全重试。
            redis.delete(key);
            throw exception;
        }
    }

    private MessagePostProcessor delayed(long millis) {
        // 为订单超时消息设置过期时间，由死信交换机转发到取消队列。
        return message -> {
            message.getMessageProperties().setExpiration(String.valueOf(millis));
            return message;
        };
    }

    public record OrderResult(String orderNo, String status) {}
}
