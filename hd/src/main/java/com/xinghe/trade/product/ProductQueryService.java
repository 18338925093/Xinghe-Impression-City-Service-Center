package com.xinghe.trade.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ProductQueryService {
    private final ProductMapper mapper;

    public ProductQueryService(ProductMapper mapper) { this.mapper = mapper; }

    public List<Product> search(String keyword) {
        // 阶段 A：商品搜索入口限制关键词长度，避免超长输入拖慢数据库查询。
        if (keyword != null && keyword.length() > 80) throw new IllegalArgumentException("商品关键词不能超过 80 个字符");
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SALE")
                .orderByDesc(Product::getUpdatedAt);
        if (StringUtils.hasText(keyword)) query.and(wrapper -> wrapper.like(Product::getName, keyword).or().like(Product::getDescription, keyword));
        return mapper.selectList(query.last("LIMIT 20"));
    }
}
