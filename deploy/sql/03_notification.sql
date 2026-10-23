-- forum_notification：站内通知、邮件发送记录
-- 对应 notification-service；只允许该服务访问本 schema。

USE forum_notification;

CREATE TABLE IF NOT EXISTS db_notification (
  id INT NOT NULL AUTO_INCREMENT,
  uid INT NOT NULL,
  title VARCHAR(255) DEFAULT NULL,
  content VARCHAR(512) DEFAULT NULL,
  type VARCHAR(64) DEFAULT NULL,
  url VARCHAR(255) DEFAULT NULL,
  -- 原实体是 String，VO 是 Date；这里统一为 DATETIME
  time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- 消费端幂等键
  event_id VARCHAR(64) DEFAULT NULL,
  -- 预留给“已读”语义；Phase 0 暂不改前端交互
  read_at DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event (event_id),
  KEY idx_uid_time (uid, time),
  KEY idx_uid_read (uid, read_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
