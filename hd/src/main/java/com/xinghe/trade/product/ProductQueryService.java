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
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SALE")
                .orderByDesc(Product::getUpdatedAt);
        if (StringUtils.hasText(keyword)) query.and(wrapper -> wrapper.like(Product::getName, keyword).or().like(Product::getDescription, keyword));
        return mapper.selectList(query.last("LIMIT 20"));
    }
}
