package com.example.metrics;

import com.example.common.observability.BusinessMetrics;
import com.example.service.NotificationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class NotificationMetrics {

    @Resource
    BusinessMetrics metrics;
    @Resource
    NotificationService notificationService;

    private final AtomicLong pending = new AtomicLong();

    @PostConstruct
    public void register() {
        metrics.gauge("forum.notification.pending", pending);
        update();
    }

    @Scheduled(fixedDelay = 60_000)
    public void update() {
        pending.set(notificationService.count());
    }
}
