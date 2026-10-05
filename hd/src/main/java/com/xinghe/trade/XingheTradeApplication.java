package com.xinghe.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.xinghe.trade")
public class XingheTradeApplication {
    // 启动 Spring Boot 应用并加载交易服务中心的全部组件。
    public static void main(String[] args) {
        SpringApplication.run(XingheTradeApplication.class, args);
    }
}
