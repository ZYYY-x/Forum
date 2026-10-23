package com.example.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 网关限流 key：优先 X-Forwarded-For 的第一个地址，否则使用远端地址。
 *
 * <p>生产环境通常由最外层 Nginx 覆盖 X-Forwarded-For；
 * 本地直连时使用 remoteAddress。</p>
 */
@Configuration
public class GatewayRateLimitConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            if (remote == null || remote.getAddress() == null) {
                return Mono.just("unknown");
            }
            return Mono.just(remote.getAddress().getHostAddress());
        };
    }
}
