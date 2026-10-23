-- forum_media：图片/头像元数据
-- 对应 media-service；文件本体在 MinIO，本库只存元数据。

USE forum_media;

CREATE TABLE IF NOT EXISTS db_image_store (
  id BIGINT NOT NULL AUTO_INCREMENT,
  uid INT NOT NULL,
  -- MinIO object name，例如 /cache/20261023/uuid 或 /avatar/uuid
  name VARCHAR(255) NOT NULL,
  content_type VARCHAR(64) DEFAULT NULL,
  size BIGINT DEFAULT NULL,
  -- 兼容原代码的 time 字段
  time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name),
  KEY idx_uid_time (uid, time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
