package com.example.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfiguration {

    @Bean
    public RequestInterceptor internalTokenInterceptor(
            @Value("${forum.internal.token:dev-internal-token}") String internalToken) {
        return template -> template.header("X-Internal-Token", internalToken);
    }
}
