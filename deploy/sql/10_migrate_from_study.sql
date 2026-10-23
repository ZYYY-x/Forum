-- 从现有 study 库迁移到四个微服务 schema
-- 前置条件：
--   1. 已执行 00_create_databases.sql、01_user.sql、02_content.sql、03_notification.sql、04_media.sql
--   2. 目标 schema 为空（只执行一次）
--   3. 已备份 study 库
--   4. 源库的 db_topic_interact_like / _collect 已修正为 PRIMARY KEY (tid, uid)
--
-- 本脚本不迁移 db_account、persistent_logins（历史遗留表）。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. 用户库
-- ---------------------------------------------------------------------------
INSERT INTO forum_user.db_account_jwt
  (id, username, password, email, role, avatar, register_time)
SELECT id, username, password, email, role, avatar, register_time
FROM study.db_account_jwt;

INSERT INTO forum_user.db_account_details
  (id, gender, phone, qq, wx, `desc`)
SELECT d.id,
       CAST(CONVERT(d.gender USING utf8mb4) AS UNSIGNED),
       d.phone, d.qq, d.wx, d.`desc`
FROM study.db_account_details d
JOIN study.db_account_jwt a ON a.id = d.id;

INSERT INTO forum_user.db_account_privacy
  (id, phone, email, wx, qq, gender)
SELECT p.id, p.phone, p.email, p.wx, p.qq, p.gender
FROM study.db_account_privacy p
JOIN study.db_account_jwt a ON a.id = p.id;

-- ---------------------------------------------------------------------------
-- 2. 内容库
-- ---------------------------------------------------------------------------
INSERT INTO forum_content.db_topic_type
  (id, name, `desc`, color)
SELECT id, name, `desc`, color
FROM study.db_topic_type;

INSERT INTO forum_content.db_topic
  (id, title, content, uid, type, time, top)
SELECT id, title, content, uid, type, time, top
FROM study.db_topic;

INSERT INTO forum_content.db_topic_comment
  (id, uid, tid, content, time, quote)
SELECT id, uid, tid, content, time, quote
FROM study.db_topic_comment;

INSERT INTO forum_content.db_topic_interact_like
  (tid, uid, time)
SELECT tid, uid, time
FROM study.db_topic_interact_like;

INSERT INTO forum_content.db_topic_interact_collect
  (tid, uid, time)
SELECT tid, uid, time
FROM study.db_topic_interact_collect;

-- 用户展示快照：只放列表展示字段
INSERT INTO forum_content.db_user_snapshot
  (uid, username, avatar, gender, show_gender, updated_at)
SELECT a.id,
       a.username,
       a.avatar,
       d.gender,
       COALESCE(p.gender, 1),
       NOW()
FROM forum_user.db_account_jwt a
LEFT JOIN forum_user.db_account_details d ON d.id = a.id
LEFT JOIN forum_user.db_account_privacy p ON p.id = a.id;

-- ---------------------------------------------------------------------------
-- 3. 通知库
-- ---------------------------------------------------------------------------
INSERT INTO forum_notification.db_notification
  (id, uid, title, content, type, url, time)
SELECT id, uid, title, content, type, url,
       COALESCE(time, NOW())
FROM study.db_notification;

-- ---------------------------------------------------------------------------
-- 4. 媒体库
-- ---------------------------------------------------------------------------
INSERT INTO forum_media.db_image_store
  (uid, name, time, created_at)
SELECT uid, name,
       COALESCE(time, NOW()),
       COALESCE(time, NOW())
FROM study.db_image_store;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 5. 校验（人工核对结果）
-- ---------------------------------------------------------------------------
SELECT 'user.account' AS item, COUNT(*) AS migrated
FROM forum_user.db_account_jwt
UNION ALL
SELECT 'user.details', COUNT(*) FROM forum_user.db_account_details
UNION ALL
SELECT 'user.privacy', COUNT(*) FROM forum_user.db_account_privacy
UNION ALL
SELECT 'content.topic_type', COUNT(*) FROM forum_content.db_topic_type
UNION ALL
SELECT 'content.topic', COUNT(*) FROM forum_content.db_topic
UNION ALL
SELECT 'content.comment', COUNT(*) FROM forum_content.db_topic_comment
UNION ALL
SELECT 'content.like', COUNT(*) FROM forum_content.db_topic_interact_like
UNION ALL
SELECT 'content.collect', COUNT(*) FROM forum_content.db_topic_interact_collect
UNION ALL
SELECT 'content.snapshot', COUNT(*) FROM forum_content.db_user_snapshot
UNION ALL
SELECT 'notification.notification', COUNT(*) FROM forum_notification.db_notification
UNION ALL
SELECT 'media.image_store', COUNT(*) FROM forum_media.db_image_store;
