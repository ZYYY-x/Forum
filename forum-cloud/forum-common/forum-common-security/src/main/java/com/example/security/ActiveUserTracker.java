package com.example.security;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * 写日活 HyperLogLog：每个通过 JWT 认证的请求记录一次 uid。
 * HLL 近似统计，误差约 0.81%。
 */
@Component
public class ActiveUserTracker {

    @Resource
    StringRedisTemplate template;

    public void mark(int uid) {
        String key = key();
        template.opsForHyperLogLog().add(key, String.valueOf(uid));
        template.expire(key, 35, TimeUnit.DAYS);
    }

    public long todayCount() {
        Long size = template.opsForHyperLogLog().size(key());
        return size == null ? 0 : size;
    }

    public static String key() {
        return "forum:dau:" + LocalDate.now();
    }
}
