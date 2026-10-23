-- Phase 0: 创建微服务使用的四个逻辑 schema
-- 适用于 MySQL 8.0+
-- 注意：本脚本只创建数据库，不删除已有库。

CREATE DATABASE IF NOT EXISTS forum_user
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS forum_content
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS forum_notification
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS forum_media
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
