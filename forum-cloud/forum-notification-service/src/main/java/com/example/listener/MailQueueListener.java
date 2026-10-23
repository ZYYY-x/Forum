package com.example.listener;

import jakarta.annotation.Resource;
import com.example.common.observability.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RabbitListener(queues = "mail")
public class MailQueueListener {

    @Resource
    JavaMailSender sender;
    @Resource
    BusinessMetrics metrics;

    @Value("${spring.mail.username:}")
    String username;

    @RabbitHandler
    public void sendMailMessage(Map<String, Object> data) {
        String mailType = String.valueOf(data.get("type"));
        var sample = metrics.start();
        try {
            String email = (String) data.get("email");
            Integer code = (Integer) data.get("code");
            String type = (String) data.get("type");
            SimpleMailMessage message = switch (type) {
                case "register" ->
                        createMessage("欢迎注册", "您的注册验证码是：" + code + "有效期三分钟", email);
                case "reset" ->
                        createMessage("密码重置邮件", "您正在尝试重置账户密码,重置验证码为" + code, email);
                case "modify" ->
                        createMessage("邮件地址修改确认", "您正在尝试修改绑定邮箱，验证码为：" + code + ",有效期三分钟", email);
                default -> throw new IllegalStateException("Unexpected value: " + type);
            };
            sender.send(message);
            metrics.increment("forum.mail.sent", "type", mailType, "result", "success");
            log.info("邮件发送成功: type={}, email={}", type, email);
        } catch (Exception e) {
            metrics.increment("forum.mail.sent", "type", mailType, "result", "failure");
            // 避免 SMTP 故障导致消息无限重投；生产环境建议接入 DLQ/重试表。
            log.error("发送邮件失败: {}", data, e);
        } finally {
            metrics.stop(sample, "forum.mail.send.duration");
        }
    }

    private SimpleMailMessage createMessage(String title, String content, String email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setSubject(title);
        message.setText(content);
        message.setTo(email);
        message.setFrom(username);
        return message;
    }
}
