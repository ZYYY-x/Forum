# SQL 使用说明

## 文件顺序

| 顺序 | 文件 | 作用 |
|---|---|---|
| 0 | `00_create_databases.sql` | 创建 `forum_user`、`forum_content`、`forum_notification`、`forum_media` |
| 1 | `01_user.sql` | 用户库 DDL |
| 2 | `02_content.sql` | 内容库 DDL |
| 3 | `03_notification.sql` | 通知库 DDL |
| 4 | `04_media.sql` | 媒体库 DDL |
| 5 | `05_seed_topic_types.sql` | 可选：5 个基础帖子类型 |
| 6 | `10_migrate_from_study.sql` | 从现有 `study` 库迁移数据 |

## 重要说明

- 根目录的 `study.sql` 是 **Navicat 原始结构导出**，开头包含 `DROP TABLE`，**不要直接对现有 `study` 库执行**。
- Phase 0 已对现有 `study` 库的 `db_topic_interact_like` / `_collect` 执行主键修复：
  `PRIMARY KEY (tid, uid)`。迁移脚本默认源库已经修复。
- Phase 0 已备份现有库到 `backups/study_*.sql`。
- 目标 schema 的 DDL 与原始 `study` 的差异：
  - `db_account_details.gender`：`tinyblob` → `TINYINT`；
  - `db_topic_interact_like/collect`：主键改为 `(tid, uid)`；
  - `db_topic.top` 映射为 `TINYINT(1)`；
  - `db_notification`：`time` 为 `DATETIME`，新增 `event_id`、`read_at`；
  - `db_image_store`：新增 `id` 主键、`content_type`、`size`、`created_at`；
  - 补充列表/评论/通知/媒体索引；
  - 不迁移 `db_account`、`persistent_logins`。

## 初始化新环境

```bash
mysql -uroot -p < deploy/sql/00_create_databases.sql
mysql -uroot -p < deploy/sql/01_user.sql
mysql -uroot -p < deploy/sql/02_content.sql
mysql -uroot -p < deploy/sql/03_notification.sql
mysql -uroot -p < deploy/sql/04_media.sql
mysql -uroot -p < deploy/sql/05_seed_topic_types.sql
```

## 从现有 study 迁移

```bash
# 1. 先备份
mysqldump -uroot -p --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 --databases study > study_backup.sql

# 2. 建库建表
mysql -uroot -p < deploy/sql/00_create_databases.sql
mysql -uroot -p < deploy/sql/01_user.sql
mysql -uroot -p < deploy/sql/02_content.sql
mysql -uroot -p < deploy/sql/03_notification.sql
mysql -uroot -p < deploy/sql/04_media.sql

# 3. 迁移数据
mysql -uroot -p --default-character-set=utf8mb4 < deploy/sql/10_migrate_from_study.sql
```

迁移脚本末尾会输出各表行数，需与 `study` 库逐项核对。

## 当前阶段

Phase 0 只完成了：

- 在现有 `study` 库上修复互动表主键；
- 提供目标 schema 的干净 DDL 和迁移脚本；
- 未切换应用数据源，现有单体仍使用 `study`。

真正的数据迁移在微服务拆分阶段执行。
