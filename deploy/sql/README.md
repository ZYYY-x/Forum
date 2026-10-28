# SQL 使用说明

## 当前目标：AWS RDS PostgreSQL

AWS RDS/Aurora PostgreSQL 使用 **一个 `postgres` 数据库 + 四个 schema**，不再使用四个独立 MySQL database。

```text
postgres
├─ forum_user
├─ forum_content
├─ forum_notification
└─ forum_media
```

DDL 在 [`postgres/`](postgres/)：

| 顺序 | 文件 | 作用 |
|---|---|---|
| 0 | `postgres/00_create_schemas.sql` | 创建四个 schema |
| 1 | `postgres/01_user.sql` | 用户/资料/隐私 |
| 2 | `postgres/02_content.sql` | 帖子/类型/评论/互动/快照 |
| 3 | `postgres/03_notification.sql` | 站内通知 |
| 4 | `postgres/04_media.sql` | 图片元数据 |
| 5 | `postgres/05_seed_topic_types.sql` | 可选：5 个基础帖子类型 |

### 初始化

```bash
psql "host=<rds-endpoint> port=5432 dbname=postgres user=postgres sslmode=require" \
  -f deploy/sql/postgres/00_create_schemas.sql

psql "host=<rds-endpoint> port=5432 dbname=postgres user=postgres sslmode=require" \
  -f deploy/sql/postgres/01_user.sql

# 02 / 03 / 04 / 05 同理
```

也可以进入 psql 后执行：

```sql
\i deploy/sql/postgres/00_create_schemas.sql
\i deploy/sql/postgres/01_user.sql
...
```

### 应用连接串

四个服务连接同一个 RDS 实例，通过 `currentSchema` 隔离：

```text
user:
jdbc:postgresql://<rds-endpoint>:5432/postgres?sslmode=require&currentSchema=forum_user

content:
jdbc:postgresql://<rds-endpoint>:5432/postgres?sslmode=require&currentSchema=forum_content

notification:
jdbc:postgresql://<rds-endpoint>:5432/postgres?sslmode=require&currentSchema=forum_notification

media:
jdbc:postgresql://<rds-endpoint>:5432/postgres?sslmode=require&currentSchema=forum_media
```

对应环境变量：

```text
FORUM_USER_DB_URL / FORUM_USER_DB_USERNAME / FORUM_USER_DB_PASSWORD
FORUM_CONTENT_DB_URL / FORUM_CONTENT_DB_USERNAME / FORUM_CONTENT_DB_PASSWORD
FORUM_NOTIFICATION_DB_URL / FORUM_NOTIFICATION_DB_USERNAME / FORUM_NOTIFICATION_DB_PASSWORD
FORUM_MEDIA_DB_URL / FORUM_MEDIA_DB_USERNAME / FORUM_MEDIA_DB_PASSWORD
```

生产环境建议：

- 使用 Secrets Manager / K8s Secret 注入密码，不写入镜像；
- RDS 安全组只允许 EKS 节点/Pod 的 VPC CIDR 或安全组；
- 强制 `sslmode=require`（更严格可下载 RDS CA 后使用 `verify-full`）；
- 连接池上限按 Pod 数 × 每 Pod 连接数评估，避免打满 RDS `max_connections`。

### 数据库账号

快速验证可以直接用 RDS master `postgres`。生产建议创建独立应用账号，并只授予所需 schema：

```sql
CREATE ROLE forum_app LOGIN PASSWORD '<strong-password>';
GRANT CONNECT ON DATABASE postgres TO forum_app;
GRANT USAGE ON SCHEMA forum_user, forum_content, forum_notification, forum_media TO forum_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA
  forum_user, forum_content, forum_notification, forum_media TO forum_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA
  forum_user, forum_content, forum_notification, forum_media TO forum_app;
```

## MySQL 旧脚本（legacy）

根目录的 `00_create_databases.sql`、`01_user.sql` 等是早期本地 MySQL 8 脚本，保留用于旧环境回归，不是 AWS 目标。

- `10_migrate_from_study.sql` 是 MySQL → MySQL 的历史数据迁移脚本；
- 如果确实要把本地 `study` 数据带到 AWS，需要单独做 MySQL → PostgreSQL 的数据抽取/转换（ETL），不能直接执行该脚本；
- 新环境建议只执行 `postgres/` 下的 DDL + 种子数据。
