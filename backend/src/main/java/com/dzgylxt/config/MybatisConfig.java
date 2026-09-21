package com.dzgylxt.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.dzgylxt.permission.DataPermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置：数据权限拦截器 + 分页拦截器。
 */
@Configuration
public class MybatisConfig {

    @Autowired(required = false)
    private DataPermissionInterceptor dataPermissionInterceptor;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 数据权限拦截器需位于分页之前
        if (dataPermissionInterceptor != null) {
            interceptor.addInnerInterceptor(dataPermissionInterceptor);
        }
        // 分页拦截器（MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
