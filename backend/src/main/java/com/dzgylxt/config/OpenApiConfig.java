package com.dzgylxt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc / OpenAPI 3.1 配置：全局 Bearer JWT 安全方案。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI supplyChainOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("供应链采购协同中台 API")
                        .description("后台 Web 管理端 + 供应商 H5 统一后端服务（OpenAPI 3.1）")
                        .version("v1.0.0"))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
