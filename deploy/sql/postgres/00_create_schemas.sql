-- ============================================================================
-- forum PostgreSQL / AWS RDS 初始化：四个逻辑 schema
-- 适用：PostgreSQL 13+ / Amazon Aurora PostgreSQL / RDS PostgreSQL
--
-- 连接示例：
--   psql "host=<rds-endpoint> port=5432 dbname=postgres user=postgres sslmode=require"
--   \i deploy/sql/postgres/00_create_schemas.sql
--
-- 说明：
-- - 只创建 schema，不创建 database；RDS 默认使用 postgres 库。
-- - 各服务通过 JDBC currentSchema=<schema> 隔离。
-- - 不删除已有对象，可重复执行。
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS forum_user;
CREATE SCHEMA IF NOT EXISTS forum_content;
CREATE SCHEMA IF NOT EXISTS forum_notification;
CREATE SCHEMA IF NOT EXISTS forum_media;

COMMENT ON SCHEMA forum_user IS 'forum user-service: account/profile/privacy';
COMMENT ON SCHEMA forum_content IS 'forum content-service: topic/comment/interaction';
COMMENT ON SCHEMA forum_notification IS 'forum notification-service: in-app notification';
COMMENT ON SCHEMA forum_media IS 'forum media-service: image metadata; binaries in S3/MinIO';
