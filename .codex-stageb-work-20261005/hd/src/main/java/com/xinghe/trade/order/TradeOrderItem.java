package com.xinghe.trade.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("trade_order_item")
public class TradeOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long skuId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineAmount() { return lineAmount; }
    public void setLineAmount(BigDecimal lineAmount) { this.lineAmount = lineAmount; }
}
