package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.api.dto.NotificationCreateRequest;
import com.example.common.observability.BusinessMetrics;
import com.example.entity.dto.Notification;
import com.example.entity.vo.response.NotificationVO;
import com.example.mapper.NotificationMapper;
import com.example.service.NotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Resource
    BusinessMetrics metrics;

    @Override
    public List<NotificationVO> findUserNotification(int uid) {
        metrics.increment("forum.notification.listed");
        return this.list(Wrappers.<Notification>query().eq("uid", uid))
                .stream()
                .map(notification -> notification.asViewObject(NotificationVO.class))
                .toList();
    }

    @Override
    public void deleteUserNotification(int id, int uid) {
        this.remove(Wrappers.<Notification>query().eq("id", id).eq("uid", uid));
        metrics.increment("forum.notification.deleted", "scope", "single");
    }

    @Override
    public void deleteUserAllNotification(int uid) {
        this.remove(Wrappers.<Notification>query().eq("uid", uid));
        metrics.increment("forum.notification.deleted", "scope", "all");
    }

    @Override
    public void addNotification(int uid, String title, String content, String type, String url) {
        Notification notification = new Notification();
        notification.setUid(uid);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setUrl(url);
        notification.setTime(new Date());
        this.save(notification);
        metrics.increment("forum.notification.created", "source", "internal");
    }

    @Override
    public void addNotification(NotificationCreateRequest request) {
        if (request.eventId() != null && this.query()
                .eq("event_id", request.eventId())
                .count() > 0) {
            metrics.increment("forum.notification.consumed", "result", "duplicate");
            return;
        }
        Notification notification = new Notification();
        notification.setUid(request.uid());
        notification.setTitle(request.title());
        notification.setContent(request.content());
        notification.setType(request.type());
        notification.setUrl(request.url());
        notification.setEventId(request.eventId());
        notification.setTime(new Date());
        try {
            this.save(notification);
            metrics.increment("forum.notification.created", "source", "event");
            metrics.increment("forum.notification.consumed", "result", "success");
        } catch (DuplicateKeyException e) {
            metrics.increment("forum.notification.consumed", "result", "duplicate");
            log.info("通知事件已消费: eventId={}", request.eventId());
        }
    }
}
