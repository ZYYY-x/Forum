# AWS RDS MySQL 改造说明

> 当前目标：AWS EKS + Amazon RDS MySQL 8.0；四个业务服务分别连接四个 database。

## 1. 数据库

```text
forum_user
forum_content
forum_notification
forum_media
```

DDL：`deploy/sql/00_create_databases.sql`、`01_user.sql`、`02_content.sql`、`03_notification.sql`、`04_media.sql`、`05_seed_topic_types.sql`。

## 2. JDBC 配置

```text
FORUM_USER_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_user?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
FORUM_CONTENT_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_content?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
FORUM_NOTIFICATION_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_notification?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
FORUM_MEDIA_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_media?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
```

- 驱动：`com.mysql.cj.jdbc.Driver`；
- 用户名/密码由 K8s Secret / Secrets Manager 注入；
- 本地 MySQL 可去掉 TLS：`useSSL=false&allowPublicKeyRetrieval=true`。

## 3. 初始化

```bash
mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/00_create_databases.sql

mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/01_user.sql
# 02 / 03 / 04 / 05 同理
```

## 4. AWS 网络/安全

- RDS 放私有子网；
- 安全组只允许 EKS 节点/Pod 的 VPC CIDR 或节点安全组访问 3306；
- 不开放公网 3306；
- 开启自动备份、加密；
- 生产使用 Secrets Manager / External Secrets。

## 5. 连接池

默认 HikariCP，`FORUM_DB_POOL_MAX` 控制每服务池大小（默认 10）。  
按 `Pod 数 × maximum-pool-size < RDS max_connections` 估算。

## 6. 迁移

- `deploy/sql/10_migrate_from_study.sql` 用于本地 MySQL `study` → 新的四个 database；
- 迁移前先备份；
- 根目录 `study.sql` 是 Navicat 原始导出，含 `DROP TABLE`，不要直接执行；
- 新 AWS 环境建议只执行 DDL + 种子数据。
