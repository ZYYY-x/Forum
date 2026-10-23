package com.example.common.observability;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 公共监控配置。
 *
 * <p>Phase 1 只建立 Actuator/Prometheus 基础和公共标签；
 * 具体业务指标在 Phase 8 通过 BusinessMetrics 统一埋点。</p>
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter forumCommonTags(
            @Value("${spring.application.name:forum-monolith}") String application,
            @Value("${forum.env:dev}") String env) {
        return MeterFilter.commonTags(Tags.of(
                "application", application,
                "env", env
        ));
    }
}
