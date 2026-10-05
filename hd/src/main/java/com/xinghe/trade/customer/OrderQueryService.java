package com.xinghe.trade.customer;

import com.xinghe.trade.order.TradeOrder;
import com.xinghe.trade.order.TradeOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OrderQueryService {
    private final TradeOrderMapper orderMapper;

    public OrderQueryService(TradeOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public OrderView query(String userId, String orderNo) {
        // 从 MySQL 查询订单并校验用户归属，客服和 Agent 都复用这一安全边界。
        // 阶段 A：服务层也校验身份和订单号，防止内部调用绕过 Controller 后出现空指针。
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(orderNo)) {
            throw new IllegalArgumentException("用户和订单号不能为空");
        }
        TradeOrder order = orderMapper.selectById(orderNo);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (!userId.equals(order.getUserId())) throw new SecurityException("无权查询该订单");
        return new OrderView(
                orderNo,
                order.getUserId(),
                order.getSkuId(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus(),
                format(order.getCreatedAt())
        );
    }

    private String format(LocalDateTime value) { return value == null ? null : value.toString(); }

    public record OrderView(String orderNo, String userId, Long skuId, Integer quantity,
                            BigDecimal amount, String status, String createdAt) {}
}
