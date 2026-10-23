package com.example.listener;

import com.example.api.dto.NotificationCreateRequest;
import com.example.common.observability.BusinessMetrics;
import com.example.service.NotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationListener {

    @Resource
    NotificationService notificationService;
    @Resource
    BusinessMetrics metrics;

    @RabbitListener(queues = "notification")
    public void onNotification(NotificationCreateRequest request) {
        var sample = metrics.start();
        try {
            notificationService.addNotification(request);
            metrics.increment("forum.notification.consume", "result", "success");
        } catch (Exception e) {
            metrics.increment("forum.notification.consume", "result", "failure");
            // 目前采用“记录并 ack”的策略，避免毒消息无限重投；
            // Phase 5 后续可接入 DLQ/重试表。
            log.error("消费通知事件失败: {}", request, e);
        } finally {
            metrics.stop(sample, "forum.notification.consume.duration");
        }
    }
}
