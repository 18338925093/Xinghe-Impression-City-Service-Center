package com.xinghe.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.xinghe.trade")
public class XingheTradeApplication {
    public static void main(String[] args) {
        SpringApplication.run(XingheTradeApplication.class, args);
    }
}
