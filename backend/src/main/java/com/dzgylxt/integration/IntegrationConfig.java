package com.dzgylxt.integration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 集成模块配置：启用定时调度，驱动 {@link IntegrationWorker} 扫描 Outbox。
 */
@Configuration
@EnableScheduling
public class IntegrationConfig {
}
