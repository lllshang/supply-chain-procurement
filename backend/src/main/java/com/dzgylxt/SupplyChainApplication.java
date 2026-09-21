package com.dzgylxt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 供应链采购协同中台 —— 后端启动类。
 *
 * <p>阶段一（T01–T05）为单模块骨架，按领域分包（identity/catalog/budget/purchase/
 * contract/order/settlement/approval/integration），后续可按需拆分为 Gradle 多模块。</p>
 */
@SpringBootApplication
@MapperScan("com.dzgylxt.mapper")
@ComponentScan(basePackages = "com.dzgylxt")
@EnableAspectJAutoProxy(exposeProxy = true)
public class SupplyChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplyChainApplication.class, args);
    }
}
