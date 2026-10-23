package com.example.gateway.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.entity.RestBean;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 网关侧 JWT 前置校验。
 *
 * <p>只校验签名/过期时间，不查 Redis 黑名单；黑名单由各业务服务的
 * JwtAuthFilter 统一校验，避免网关与 servlet 公共模块耦合。</p>
 */
@Component
public class GatewayJwtFilter implements GlobalFilter, Ordered {

    private final JWTVerifier verifier;
    private final boolean enabled;
    private final List<String> publicPrefixes;
    private final MeterRegistry registry;

    public GatewayJwtFilter(
            @Value("${spring.security.jwt.key}") String key,
            @Value("${forum.gateway.jwt.enabled:true}") boolean enabled,
            @Value("${forum.gateway.jwt.public-paths:/api/auth/,/images/}") String publicPaths,
            MeterRegistry registry) {
        this.verifier = JWT.require(Algorithm.HMAC256(key)).build();
        this.enabled = enabled;
        this.publicPrefixes = Arrays.stream(publicPaths.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        this.registry = registry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled || isPreflight(exchange) || isPublic(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = convertToken(authorization);
        if (token == null) {
            return unauthorized(exchange, "未登录或登录已过期", "missing");
        }

        try {
            verifier.verify(token);
        } catch (JWTVerificationException e) {
            return unauthorized(exchange, "登录状态无效，请重新登录", "invalid");
        }
        return chain.filter(exchange);
    }

    private boolean isPreflight(ServerWebExchange exchange) {
        return "OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name());
    }

    private boolean isPublic(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return publicPrefixes.stream().anyMatch(path::startsWith);
    }

    private String convertToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message, String reason) {
        registry.counter("forum.gateway.auth_rejected", "reason", reason).increment();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = RestBean.unauthorized(message).asJsonString()
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
