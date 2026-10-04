package com.xinghe.trade.logistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinghe.trade.customer.OrderQueryService;
import org.springframework.stereotype.Service;

@Service
public class LogisticsQueryService {
    private final LogisticsMapper mapper;
    private final OrderQueryService orderQueryService;

    public LogisticsQueryService(LogisticsMapper mapper, OrderQueryService orderQueryService) {
        this.mapper = mapper;
        this.orderQueryService = orderQueryService;
    }

    public LogisticsView query(String userId, String orderNo) {
        orderQueryService.query(userId, orderNo);
        LogisticsRecord record = mapper.selectOne(new LambdaQueryWrapper<LogisticsRecord>().eq(LogisticsRecord::getOrderNo, orderNo));
        if (record == null) return new LogisticsView(orderNo, null, null, "NOT_SYNCED", "物流信息尚未同步", null);
        return new LogisticsView(record.getOrderNo(), record.getCompany(), record.getTrackingNo(), record.getStatus(), record.getDescription(), record.getUpdatedAt());
    }

    public record LogisticsView(String orderNo, String company, String trackingNo, String status, String description, java.time.LocalDateTime updatedAt) {}
}
