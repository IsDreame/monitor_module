package cn.nebulaedata.cccs.acutor_module.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

// 可选：自定义健康检查指示器示例
@Component
class CustomHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // 这里可以添加自定义的健康检查逻辑
        return Health.up().withDetail("custom", "check passed").build();
    }
}