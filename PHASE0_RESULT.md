# Phase 0 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 基线修复、SQL 清洗、数据库备份、Spring Cloud 兼容性验证 |
| 状态 | **已完成** |
| 代码改动 | 后端 9 个文件、前端 2 个文件；新增 SQL/说明文件 |
| 未做 | 未切换到微服务、未启动完整应用做端到端回归 |

---

## 1. 已完成事项

### 1.1 基线代码修复

| # | 问题 | 修改 |
|---|---|---|
| 1 | 修改密码判断反了：旧密码正确时返回“原密码错误” | `AccountServiceImpl.changePassword` 改为 `!encoder.matches(...)` |
| 2 | `/api/notification/**` 被 `permitAll()` | 改为 `.authenticated()` |
| 3 | 改帖后列表缓存不失效 | `TopicServiceImpl.updateTopic` 增加 `forum:preview:*` 清理 |
| 4 | 不存在的帖子会导致 `BeanUtils.copyProperties(null, ...)` 异常 | `getTopic` 返回 null，Controller 返回 404 业务码 |
| 5 | 通知实体 `String time` 与 VO `Date time` 类型不一致 | 实体改为 `Date`，新增通知时写入 `new Date()` |
| 6 | 编辑帖子类型被硬编码 `@Max(5)` 限制 | 移除 `@Max(5)`，仍保留 `@Min(1)` + 服务端类型校验 |
| 7 | 头像超过 1MB 时前端仍继续上传 | `beforeAvatarUpload` 增加 `return false` |
| 8 | 帖子字数限制三处不一致（1000/20000/10000） | UI 与前端检查统一为 10000，与后端一致 |
| 9 | `application.yml` 明文保存密钥 | 改为环境变量占位 + `.env.example` |
| 10 | `ca.jks` 被 git add | `git rm --cached`，加入 `.gitignore`；本地文件保留 |

### 1.2 数据库备份

- 备份文件：`backups/study_20261023_185839.sql`（23,346 字节，包含 12 张表结构和 10 张表数据）。
- Git 工作区备份：
  - `backups/backend-jwt-working-tree_20261023_185847.patch`
  - `backups/frontend-jwt-working-tree_20261023_185847.patch`
  - 对应的 `*-status_*.txt`
- 备份目录不在 Git 仓库内；已添加 `backups/.gitignore` 防止未来根仓库误提交。
- 注意：`backend-jwt-working-tree_*.patch` 里可能包含旧版明文配置，属于敏感备份，不要上传/分享。

### 1.3 现有 `study` 库安全修复

已执行：

```sql
ALTER TABLE db_topic_interact_like
  MODIFY uid INT NOT NULL, DROP PRIMARY KEY, ADD PRIMARY KEY (tid, uid);

ALTER TABLE db_topic_interact_collect
  MODIFY uid INT NOT NULL, DROP PRIMARY KEY, ADD PRIMARY KEY (tid, uid);
```

校验结果：

- `db_topic_interact_like` 主键：`tid, uid`，6 行；
- `db_topic_interact_collect` 主键：`tid, uid`，5 行；
- 迁移前后行数一致，无数据丢失；
- 事务内验证：同一 `tid=8` 插入第二个 `uid=5` 后计数为 2，回滚后恢复 6 行，说明多用户点赞已可用。

### 1.4 SQL 清洗

新增 `deploy/sql/`：

| 文件 | 内容 |
|---|---|
| `00_create_databases.sql` | 创建四个 schema |
| `01_user.sql` | 用户库 DDL（gender 修正、索引、FK） |
| `02_content.sql` | 内容库 DDL（互动表主键修正、user_snapshot、索引） |
| `03_notification.sql` | 通知库 DDL（time 统一、event_id、read_at） |
| `04_media.sql` | 媒体库 DDL（id 主键、content_type、size） |
| `05_seed_topic_types.sql` | 5 个基础帖子类型 |
| `10_migrate_from_study.sql` | 从 `study` 迁移数据 |
| `README.md` | 初始化/迁移说明 |

根目录 `study.sql` 增加了警告头，明确它是原始 Navicat 导出、包含 `DROP TABLE`、互动表主键仍是错的，不应直接执行。

### 1.5 SQL 实际验证

已在本机 MySQL 8.0.32 上执行：

1. `00`~`04`：四个 schema 和全部表创建成功；
2. `10_migrate_from_study.sql`：迁移成功；
3. `05_seed_topic_types.sql`：执行成功，中文编码校验通过（`HEX` 为 UTF-8）。

迁移校验结果：

| 表 | 行数 |
|---|---:|
| `forum_user.db_account_jwt` | 5 |
| `forum_user.db_account_details` | 5 |
| `forum_user.db_account_privacy` | 5 |
| `forum_content.db_topic_type` | 5 |
| `forum_content.db_topic` | 16 |
| `forum_content.db_topic_comment` | 18 |
| `forum_content.db_topic_interact_like` | 6 |
| `forum_content.db_topic_interact_collect` | 5 |
| `forum_content.db_user_snapshot` | 5 |
| `forum_notification.db_notification` | 0 |
| `forum_media.db_image_store` | 32 |

> 注意：`forum_*` 四个 schema 已在本机创建并完成一次验证性迁移。如果后续要重新迁移，先清空/删除这四个 schema，再重新执行脚本，避免主键/唯一键冲突。现有单体应用仍然使用 `study`，没有切换数据源。

### 1.6 Spring Cloud 兼容性验证

本机原本没有 Maven，已临时下载 Apache Maven 3.9.9 到：

```text
%TEMP%\apache-maven-3.9.9
```

用以下组合做了最小验证：

| 组件 | 版本 |
|---|---|
| Spring Boot | 3.1.5 |
| Java | 17.0.9 |
| Spring Cloud | 2022.0.4 |
| Spring Cloud Alibaba | 2022.0.0.0 |
| Spring Cloud Gateway | 4.0.7 |
| Spring Cloud OpenFeign | 4.0.4 |
| Nacos Client | 2.2.1 |
| Micrometer | 1.11.5 |

验证步骤：

1. 创建一个临时 POM，引入 Gateway、LoadBalancer、OpenFeign、Nacos Discovery/Config、Redis Reactive、Actuator、Prometheus；
2. `mvn dependency:tree` 解析成功；
3. 创建一个最小 Spring Boot 应用，关闭 Nacos 发现/配置（本机未启动 Nacos），启动上下文；
4. 输出 `SPIKE_CONTEXT_OK`，应用正常关闭。

结论：

- 依赖树没有出现 Spring Boot 3.1.5 与 Spring Cloud 2022.0.4 / Alibaba 2022.0.0.0 的解析冲突；
- Gateway + WebFlux + Nacos Client + OpenFeign + Micrometer 能同时存在于同一应用；
- Nacos Client 启动时会打印 `nacos-logback.xml` 加载警告，但不影响上下文启动；
- 真正的 Nacos 注册、配置中心、Gateway 路由到服务，需要在 Phase 2 启动 Nacos 后联调。

### 1.7 构建验证

**后端：**

```text
mvn -B -DskipTests package
BUILD SUCCESS
Compiling 76 source files
Total time: 50.355 s
```

**前端：**

```text
npm run build
✓ built in 22.45s
```

仅有 Vite 的大 chunk 警告，无编译错误。

---

## 2. 关键文件变更

### 后端

- `src/main/java/com/example/service/impl/AccountServiceImpl.java`
- `src/main/java/com/example/config/SecurityConfiguration.java`
- `src/main/java/com/example/service/impl/TopicServiceImpl.java`
- `src/main/java/com/example/controller/ForumController.java`
- `src/main/java/com/example/entity/dto/Notification.java`
- `src/main/java/com/example/service/impl/NotificationServiceImpl.java`
- `src/main/java/com/example/entity/vo/request/TopicUpdateVO.java`
- `src/main/resources/application.yml`
- `.gitignore`
- 新增 `.env.example`

### 前端

- `src/views/settings/UserSetting.vue`
- `src/components/TopicEditor.vue`

### SQL / 文档

- `deploy/sql/00_create_databases.sql`
- `deploy/sql/01_user.sql`
- `deploy/sql/02_content.sql`
- `deploy/sql/03_notification.sql`
- `deploy/sql/04_media.sql`
- `deploy/sql/05_seed_topic_types.sql`
- `deploy/sql/10_migrate_from_study.sql`
- `deploy/sql/README.md`
- `study.sql`（仅增加警告头）

---

## 3. 尚未完成 / 需要你处理

1. **轮换已泄露的凭据**  
   原 `application.yml` 中的邮箱授权码、天气 Key、数据库/MinIO/Rabbit 密码、JWT 密钥曾出现在工作区/历史中。现在配置已改为环境变量，但旧凭据仍应尽快在对应平台重置。

2. **设置本地环境变量**  
   参考 `backend-jwt/.env.example`。不设置 `FORUM_MAIL_PASSWORD`、`FORUM_WEATHER_KEY` 等时，相关功能会失败，但应用可以启动。

3. **删除本地 `ca.jks` 或明确用途**  
   文件已不再被 Git 跟踪，但仍在 `backend-jwt/src/main/resources/ca.jks`。当前配置没有引用它；如果只是历史遗留，建议删除或移到仓库外。

4. **端到端启动未验证**  
   当前 Redis、RabbitMQ 服务是停止的，MinIO 未监听 9000，因此没有启动完整应用做登录/发帖回归。Phase 0 只做了编译和 SQL/兼容性验证。

5. **`study` 库的 gender 仍是 tinyblob**  
   新 schema 已修正，但现有单体仍读 `study`；在正式迁移前不要改，避免影响当前单体。

6. **`forum_*` 是验证性数据**  
   已执行过一次迁移；正式切换前建议清空后重新迁移，确保数据基线一致。

---

## 4. 下一步（Phase 1）

建议下一阶段：

1. 新建 `forum-cloud` 父 POM；
2. 创建 `forum-common-*` 子模块和 `forum-api`；
3. 把 `RestBean/BaseData/Const/JWT/异常/限流/缓存/指标` 按职责迁入；
4. 把现有单体作为 `forum-monolith` 过渡模块，保证每一步可运行；
5. 接入 Actuator/Prometheus 基础依赖。

是否继续 Phase 1，请确认。
