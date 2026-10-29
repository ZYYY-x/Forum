# SQL 使用说明

## 当前目标：AWS RDS MySQL 8.0

四个业务数据库：

```text
forum_user
forum_content
forum_notification
forum_media
```

DDL 文件：

| 顺序 | 文件 | 作用 |
|---|---|---|
| 0 | `00_create_databases.sql` | 创建四个 database |
| 1 | `01_user.sql` | 用户/资料/隐私 |
| 2 | `02_content.sql` | 帖子/类型/评论/互动/快照 |
| 3 | `03_notification.sql` | 站内通知 |
| 4 | `04_media.sql` | 图片元数据 |
| 5 | `05_seed_topic_types.sql` | 可选：5 个基础帖子类型 |
| 6 | `10_migrate_from_study.sql` | 本地 `study` 库历史数据迁移（MySQL） |

## 初始化 AWS RDS MySQL

```bash
mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/00_create_databases.sql

mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/01_user.sql

mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/02_content.sql

mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/03_notification.sql

mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/04_media.sql

# 可选
mysql -h <rds-endpoint> -P 3306 -u <master-user> -p \
  < deploy/sql/05_seed_topic_types.sql
```

每个 DDL 内部有 `USE forum_*`，无需额外指定 database。

## 应用连接串

```text
FORUM_USER_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_user?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
FORUM_CONTENT_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_content?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
FORUM_NOTIFICATION_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_notification?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
FORUM_MEDIA_DB_URL=jdbc:mysql://<rds-endpoint>:3306/forum_media?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
```

生产环境：

- 使用 Secrets Manager / K8s Secret 注入密码；
- RDS 安全组只允许 EKS 节点/Pod 的 VPC CIDR 或安全组；
- 建议用 TLS：`sslMode=REQUIRED`；
- 连接池上限按 Pod 数 × 每 Pod 连接数评估，避免打满 RDS `max_connections`。

## 本地 MySQL 初始化

```bash
mysql -uroot -p < deploy/sql/00_create_databases.sql
mysql -uroot -p < deploy/sql/01_user.sql
mysql -uroot -p < deploy/sql/02_content.sql
mysql -uroot -p < deploy/sql/03_notification.sql
mysql -uroot -p < deploy/sql/04_media.sql
mysql -uroot -p < deploy/sql/05_seed_topic_types.sql
```

本地 URL 可去掉 TLS：

```text
jdbc:mysql://localhost:3306/forum_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8
```

## 数据迁移

`10_migrate_from_study.sql` 是 MySQL → MySQL 迁移：

```bash
mysql -uroot -p < deploy/sql/00_create_databases.sql
mysql -uroot -p < deploy/sql/01_user.sql
mysql -uroot -p < deploy/sql/02_content.sql
mysql -uroot -p < deploy/sql/03_notification.sql
mysql -uroot -p < deploy/sql/04_media.sql
mysql -uroot -p --default-character-set=utf8mb4 < deploy/sql/10_migrate_from_study.sql
```

> 根目录 `study.sql` 是 Navicat 原始导出，含 `DROP TABLE`，不要直接对现有库执行。
> 迁移前请先备份；迁移脚本默认源库互动表主键已修复为 `(tid, uid)`。
