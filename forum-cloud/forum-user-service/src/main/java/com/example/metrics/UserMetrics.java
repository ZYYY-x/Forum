package com.example.metrics;

import com.example.common.observability.BusinessMetrics;
import com.example.security.ActiveUserTracker;
import com.example.service.AccountService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class UserMetrics {

    @Resource
    BusinessMetrics metrics;
    @Resource
    ActiveUserTracker activeUserTracker;
    @Resource
    AccountService accountService;

    private final AtomicLong dau = new AtomicLong();
    private final AtomicLong users = new AtomicLong();

    @PostConstruct
    public void register() {
        metrics.gauge("forum.dau", dau);
        metrics.gauge("forum.users.total", users);
        update();
    }

    @Scheduled(fixedDelay = 60_000)
    public void update() {
        dau.set(activeUserTracker.todayCount());
        users.set(accountService.count());
    }
}
