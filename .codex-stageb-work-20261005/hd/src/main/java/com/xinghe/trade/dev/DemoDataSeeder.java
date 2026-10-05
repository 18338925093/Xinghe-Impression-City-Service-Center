package com.xinghe.trade.dev;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinghe.trade.logistics.LogisticsMapper;
import com.xinghe.trade.logistics.LogisticsRecord;
import com.xinghe.trade.order.TradeOrder;
import com.xinghe.trade.order.TradeOrderItem;
import com.xinghe.trade.order.TradeOrderItemMapper;
import com.xinghe.trade.order.TradeOrderMapper;
import com.xinghe.trade.product.Product;
import com.xinghe.trade.product.ProductMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "xinghe.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
    private static final Long DEMO_PRODUCT_ID = 10001L;
    private static final String DEMO_ORDER_NO = "XH2026100300000001";

    private final ProductMapper productMapper;
    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;
    private final LogisticsMapper logisticsMapper;
    private final RedisTemplate<String, Object> redis;

    public DemoDataSeeder(ProductMapper productMapper,
                          TradeOrderMapper orderMapper,
                          TradeOrderItemMapper orderItemMapper,
                          LogisticsMapper logisticsMapper,
                          RedisTemplate<String, Object> redis) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.logisticsMapper = logisticsMapper;
        this.redis = redis;
    }

    @Override
    public void run(String... args) {
        // 应用启动时按配置写入可重复执行的开发演示数据。
        seed();
    }

    private void seed() {
        // 初始化商品、Redis 库存、订单明细和物流记录，支撑客服联调演示。
        LocalDateTime now = LocalDateTime.now();
        Product product = productMapper.selectById(DEMO_PRODUCT_ID);
        if (product == null) {
            product = new Product();
            product.setId(DEMO_PRODUCT_ID);
            product.setStoreId(1L);
            product.setName("星河精选运动鞋");
            product.setDescription("用于客服 Agent 商品查询和交易联调的演示商品");
            product.setPrice(new BigDecimal("199.00"));
            product.setStatus("ON_SALE");
            product.setCreatedAt(now);
            product.setUpdatedAt(now);
            productMapper.insert(product);
        }
        redis.opsForValue().setIfAbsent("stock:sku:" + DEMO_PRODUCT_ID, "20");

        TradeOrder order = orderMapper.selectById(DEMO_ORDER_NO);
        if (order == null) {
            order = new TradeOrder();
            order.setOrderNo(DEMO_ORDER_NO);
            order.setUserId("user-1001");
            order.setSkuId(DEMO_PRODUCT_ID);
            order.setQuantity(1);
            order.setAmount(new BigDecimal("199.00"));
            order.setStatus("PAID");
            order.setCreatedAt(now.minusHours(2));
            order.setUpdatedAt(now.minusHours(1));
            orderMapper.insert(order);
        }

        Long itemCount = orderItemMapper.selectCount(new LambdaQueryWrapper<TradeOrderItem>()
                .eq(TradeOrderItem::getOrderNo, DEMO_ORDER_NO));
        if (itemCount == 0) {
            TradeOrderItem item = new TradeOrderItem();
            item.setOrderNo(DEMO_ORDER_NO);
            item.setSkuId(DEMO_PRODUCT_ID);
            item.setQuantity(1);
            item.setUnitPrice(new BigDecimal("199.00"));
            item.setLineAmount(new BigDecimal("199.00"));
            orderItemMapper.insert(item);
        }

        LogisticsRecord logistics = logisticsMapper.selectOne(new LambdaQueryWrapper<LogisticsRecord>()
                .eq(LogisticsRecord::getOrderNo, DEMO_ORDER_NO));
        if (logistics == null) {
            logistics = new LogisticsRecord();
            logistics.setOrderNo(DEMO_ORDER_NO);
            logistics.setCompany("星河速运");
            logistics.setTrackingNo("XH-DEMO-TRACK-001");
            logistics.setStatus("IN_TRANSIT");
            logistics.setDescription("包裹已到达星河印象城配送站");
            logistics.setUpdatedAt(now);
            logisticsMapper.insert(logistics);
        }
    }
}
