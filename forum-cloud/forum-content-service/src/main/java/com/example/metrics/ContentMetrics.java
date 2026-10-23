package com.example.metrics;

import com.example.common.observability.BusinessMetrics;
import com.example.mapper.TopicCommentMapper;
import com.example.service.TopicService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ContentMetrics {

    @Resource
    BusinessMetrics metrics;
    @Resource
    TopicService topicService;
    @Resource
    TopicCommentMapper commentMapper;

    private final AtomicLong topics = new AtomicLong();
    private final AtomicLong comments = new AtomicLong();

    @PostConstruct
    public void register() {
        metrics.gauge("forum.topic.total", topics);
        metrics.gauge("forum.comment.total", comments);
        update();
    }

    @Scheduled(fixedDelay = 60_000)
    public void update() {
        topics.set(topicService.count());
        comments.set(commentMapper.selectCount(null));
    }
}
