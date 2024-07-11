package com.zhaoyu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.zhaoyu.mapper")
@SpringBootApplication
public class reviewConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(reviewConnectApplication.class, args);
    }

}
