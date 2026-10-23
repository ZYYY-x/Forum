-- forum_content：帖子、类型、评论、互动、用户展示快照
-- 对应 content-service；只允许该服务访问本 schema。

USE forum_content;

CREATE TABLE IF NOT EXISTS db_topic_type (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) DEFAULT NULL,
  `desc` VARCHAR(255) DEFAULT NULL,
  color VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS db_topic (
  id INT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) DEFAULT NULL,
  content MEDIUMTEXT,
  uid INT DEFAULT NULL,
  type INT DEFAULT NULL,
  time DATETIME DEFAULT NULL,
  top TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_type_time (type, time),
  KEY idx_uid (uid),
  KEY idx_top_time (top, time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS db_topic_comment (
  id INT NOT NULL AUTO_INCREMENT,
  uid INT DEFAULT NULL,
  tid INT DEFAULT NULL,
  content TEXT,
  time DATETIME DEFAULT NULL,
  quote INT DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_tid_time (tid, time),
  KEY idx_uid (uid)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- 重要：主键必须是 (tid, uid)。
-- 原 study 库是 PRIMARY KEY (tid)，导致同一帖子只能记录一个用户的点赞。
CREATE TABLE IF NOT EXISTS db_topic_interact_like (
  tid INT NOT NULL,
  uid INT NOT NULL,
  time DATETIME DEFAULT NULL,
  PRIMARY KEY (tid, uid),
  KEY idx_uid (uid),
  KEY idx_tid_time (tid, time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS db_topic_interact_collect (
  tid INT NOT NULL,
  uid INT NOT NULL,
  time DATETIME DEFAULT NULL,
  PRIMARY KEY (tid, uid),
  KEY idx_uid (uid),
  KEY idx_tid_time (tid, time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- content-service 本地用户快照：只放列表展示所需的轻量字段，不复制手机号/邮箱等 PII。
CREATE TABLE IF NOT EXISTS db_user_snapshot (
  uid INT NOT NULL,
  username VARCHAR(64) DEFAULT NULL,
  avatar VARCHAR(255) DEFAULT NULL,
  gender TINYINT DEFAULT NULL,
  show_gender TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (uid),
  KEY idx_username (username)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
