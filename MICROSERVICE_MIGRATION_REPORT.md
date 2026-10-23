# 校园论坛微服务化与 Prometheus 业务监控改造报告

| 项目 | 内容 |
|---|---|
| 报告版本 | v1.0（待审阅） |
| 日期 | 2026-10-23 |
| 改造对象 | `backend-jwt`（Spring Boot 3.1.5 单体）、`frontend-jwt`（Vue 3）、`study.sql` |
| 目标 | 拆分为可独立部署的微服务；后端接入 Prometheus 业务指标监控 |
| 当前状态 | Phase 0~9 已完成；详见 `PHASE0_RESULT.md` ~ `PHASE9_RESULT.md` |
| 实施前提 | 已完成阶段化改造；K8s 清单由部署侧维护，当前环境保持运行 |

---

## 0. 结论摘要

### 0.1 推荐目标形态

采用“**1 个网关 + 5 个业务服务**”的拆分：

```text
forum-gateway       4433  /  管理端口 9080
forum-user-service  8081  /  管理端口 9081   forum_user
forum-content-service 8082 / 管理端口 9082   forum_content
forum-notification-service 8083 / 9083       forum_notification
forum-media-service 8084 / 9084              forum_media
forum-weather-service 8085 / 9085            无库
```

- 注册/配置中心：Nacos。
- 服务间同步调用：OpenFeign + LoadBalancer。
- 异步事件：RabbitMQ（通知、邮件）。
- 数据库：开发环境一个 MySQL 实例、四个逻辑 schema；生产环境可拆实例。
- 监控：Spring Boot Actuator + Micrometer + Prometheus + Grafana + Alertmanager。
- 前端：**尽量不改页面**，网关继续监听 `4433`，保持现有 API 路径；只把 `baseURL` 改成环境变量。

### 0.2 主要工作量

| 阶段 | 内容 | 预估人天 |
|---|---|---|
| Phase 0 | 基线修复、SQL 清洗、兼容性验证 | 1–2 |
| Phase 1 | 父工程、common、api 模块、单体模块化 | 2–3 |
| Phase 2 | 网关、Nacos、路由切换 | 1–2 |
| Phase 3 | user-service 抽取 | 2–3 |
| Phase 4 | content-service 抽取（最复杂） | 3–4 |
| Phase 5 | notification-service 抽取 | 1–2 |
| Phase 6 | media-service 抽取 | 1–2 |
| Phase 7 | weather-service 抽取 | 0.5–1 |
| Phase 8 | Prometheus/Grafana/告警 | 2–3 |
| Phase 9 | Docker Compose、文档、回归测试 | 2–3 |
| **合计** | | **16–25 人天（约 3–5 周）** |

如果时间紧，可先做“网关 + user + content + notification”的最小版本，media/weather 暂时作为 content 的内部模块，仍可按服务维度埋点；完整版再拆。

### 0.3 需要你先确认的 10 个决策

见第 10 节。最关键的是：

1. 是否接受“1 网关 + 5 业务服务”的粒度；
2. 是否接受 Nacos；
3. 是否保持 Spring Boot 3.1.5（低风险）而不是同步升级；
4. 是否接受开发环境“一个 MySQL + 四个 schema”；
5. 是否接受网关继续用 4433、前端只改 baseURL；
6. 是否接受媒体/天气服务独立（可合并）。

---

## 1. 现状评估

### 1.1 现有代码结构

后端目前是标准单体：

```text
backend-jwt/src/main/java/com/example
├── config/        SecurityConfiguration、WebConfiguration、MinioConfiguration、RabbitConfiguration
├── controller/    Account、Authorize、Forum、Image、Notification、Object、Test
├── entity/        dto、vo/request、vo/response、BaseData、RestBean
├── filter/        CorsFilter、FlowLimitFilter、JWTAuthorizeFilter
├── listener/      MailQueueListener
├── mapper/        Account*、Topic*、ImageStore、Notification
├── service/       Account*、Topic*、Image*、Notification*、Weather*
└── utils/         JWTUtils、FlowUtils、CacheUtils、Const、ControllerUtils
```

关键耦合点：

- 所有业务共用一个 `study` 数据库；
- `TopicServiceImpl` 直接查 `db_account_jwt`、`db_account_details`、`db_account_privacy` 来拼作者信息；
- `ImageServiceImpl` 直接更新 `db_account_jwt.avatar`；
- `NotificationServiceImpl` 直接写 `db_notification`；
- 所有请求共用 `FlowLimitFilter`、`CorsFilter`、JWT 过滤器；
- 登录用 Spring Security 的 `formLogin`，JWT 由 successHandler 返回。

这些是拆分时真正要处理的地方，而不是简单把 Controller 复制到不同工程。

### 1.2 `study.sql` 评估

`study.sql` 已经提供，解决了“仓库里没有 schema”的问题，但它是 **Navicat 导出的结构脚本，不含 INSERT 数据**，且包含需要清理的历史问题：

> ⚠️ 脚本开头是 `DROP TABLE IF EXISTS`，**不要在现有 `study` 库或有数据的库上直接执行**；它只适合在空库/新 schema 初始化结构。现有数据请先 `mysqldump` 备份。

| 问题 | 影响 | 处理建议 |
|---|---|---|
| `db_topic_interact_like/collect` 主键是 `(tid)` | 同一帖子只能存一条点赞/收藏，`insert ignore` 会静默丢掉第二个用户 | 改为 `PRIMARY KEY (tid, uid)` |
| `db_account_details.gender` 是 `tinyblob` | 现在存的是 ASCII `"0"`，类型语义错误 | 转成 `TINYINT NOT NULL DEFAULT 0` |
| `db_account`、`persistent_logins` | 当前代码未使用的历史表 | 备份后删除 |
| `db_notification.time` 是 `datetime`，实体是 `String`，VO 是 `Date` | 一旦有非空时间，`BaseData.asViewObject` 可能类型不匹配 | 实体/VO 统一为 `Date` |
| `db_topic.top` 不在实体中 | 置顶只能手工改库 | 在 content-service 实体中补 `top`，或保留手工维护并写明 |
| `db_image_store` 无主键 | 无法唯一定位记录、清理困难 | 增加 `id` 主键及 `content_type/size/created_at` |
| 缺少业务索引 | 列表、评论、通知查询会随数据量变慢 | 补 `(type,time)`、`(tid,time)`、`(uid,time)` 等 |
| 无种子数据 | 新环境无法直接演示 | 另出一份 `seed.sql`（可选） |

### 1.3 拆分前必须先修的基线问题

这些不修，拆完后会变成跨服务 bug：

1. **修改密码判断反了**  
   `AccountServiceImpl.changePassword` 在旧密码正确时返回“原密码错误”，旧密码错误时反而修改成功。必须先修。

2. **点赞/收藏主键错误**  
   这是数据库层问题，拆到 content-service 后仍然是错的，必须先改。

3. **通知接口 `permitAll`**  
   `/api/notification/**` 目前放行；拆到 notification-service 后应改为需要认证。

4. **明文密钥**  
   `application.yml` 的邮箱授权码、天气 key、数据库/MinIO 密码、JWT key，以及被 `git add` 的 `ca.jks`，拆分前应改为环境变量。

5. **头像上传大小校验**  
   前端 `beforeAvatarUpload` 超 1MB 时没有 `return false`；拆分 media-service 时顺手修复。

6. **字数限制不一致**  
   UI 写 1000、前端检查 20000、后端检查 10000；拆分时统一为后端常量并在前端展示。

7. **改帖缓存不失效、非法 tid 会 NPE**  
   拆 content-service 时一并修。

### 1.4 拆微服务的主要难点

| 难点 | 现状 | 方案 |
|---|---|---|
| 作者信息跨表 | content 直接查 user 三张表 | user-service 提供内部 profile API；content 维护轻量 `db_user_snapshot` |
| 头像字段归属 | media 直接更新 `db_account_jwt.avatar` | media 存文件，user-service 拥有 avatar 字段，内部接口绑定 |
| 通知写入 | content 直接插入通知 | content 发 `notification.created` 事件，notification-service 消费 |
| 邮件 | 单体里 Rabbit 监听 + SMTP | user-service 发 `mail.requested`，notification-service 统一发送 |
| 鉴权 | 单体一个 JWT 过滤器 | 抽到 `common-security`，各服务复用；网关做前置校验 |
| 限流/CORS | 单体两个 Filter | 网关统一做全局限流/CORS；业务限流留在服务内 |
| 天气路径 | `/api/forum/weather` 在 ForumController | 网关把该路径优先路由到 weather-service |
| 数据迁移 | 一个库 | 四个 schema，按服务复制表；不引入 Seata |

---

## 2. 目标架构

### 2.1 总览

```text
                         ┌──────────────────────────────┐
                         │        Vue3 SPA (5173)       │
                         │  axios baseURL = gateway:4433│
                         └──────────────┬───────────────┘
                                        │
                         ┌──────────────▼───────────────┐
                         │        forum-gateway         │
                         │  CORS / 全局限流 / 请求ID     │
                         │  JWT 前置校验（可选）          │
                         └───┬───────┬───────┬───────┬──┘
                             │       │       │       │
              ┌──────────────▼─┐ ┌───▼────┐ ┌▼──────┐ ┌▼─────────┐
              │  user-service  │ │content │ │notify │ │  media   │
              │  auth/user     │ │ forum  │ │ 消息  │ │ 图片/头像 │
              └───────┬────────┘ └───┬────┘ └───┬───┘ └────┬─────┘
                      │              │          │          │
              ┌───────▼────┐ ┌───────▼────┐ ┌───▼────┐ ┌───▼─────┐
              │ forum_user │ │forum_content│ │forum_  │ │forum_   │
              │            │ │             │ │notify  │ │media    │
              └────────────┘ └────────────┘ └────────┘ └─────────┘
                      │              │          │          │
                      └──────────────┴──────────┴──────────┘
                                  Redis / RabbitMQ / MinIO
                                        │
                              ┌─────────▼─────────┐
                              │ weather-service   │──→ QWeather API
                              │  无库，Redis 缓存  │
                              └───────────────────┘

                Prometheus ← 各服务 /actuator/prometheus（独立管理端口）
                     │
                  Grafana / Alertmanager
```

### 2.2 服务清单

| 服务 | 端口 | 管理端口 | API 前缀 | 数据库 | 职责 |
|---|---|---|---|---|---|
| `forum-gateway` | 4433 | 9080 | 全部 `/api/**` | 无 | 路由、CORS、全局限流、请求 ID、JWT 前置校验 |
| `forum-user-service` | 8081 | 9081 | `/api/auth/**`、`/api/user/**` | `forum_user` | 注册登录、验证码、JWT、资料、隐私、头像字段 |
| `forum-content-service` | 8082 | 9082 | `/api/forum/**`（天气除外） | `forum_content` | 帖子、类型、评论、点赞收藏、缓存 |
| `forum-notification-service` | 8083 | 9083 | `/api/notification/**` | `forum_notification` | 站内通知、邮件发送 |
| `forum-media-service` | 8084 | 9084 | `/api/image/**`、`/images/**` | `forum_media` | MinIO 上传、图片读取、头像存储 |
| `forum-weather-service` | 8085 | 9085 | `/api/forum/weather` | 无 | QWeather 集成、Redis 缓存 |

> 说明：`content-service` 暂不拆“interaction-service”。点赞/收藏与帖子、评论同属内容域，强一致和查询聚合更简单；未来若互动量很大，再单独拆。

### 2.3 Maven 模块结构

建议新建 `forum-cloud/`，不破坏现有 `backend-jwt/`，验证后再归档旧工程。

```text
forum-cloud/
├── pom.xml                              # 父 POM，dependencyManagement
├── forum-common/                        # 公共代码父模块（packaging pom）
│   ├── forum-common-core/               # RestBean、BaseData、Const、异常、分页 DTO；不依赖 servlet
│   ├── forum-common-observability/      # MetricsConfig、BusinessMetrics、指标常量；不依赖 servlet
│   ├── forum-common-security/           # JwtService、JwtAuthFilter、CurrentUser；servlet 专用
│   ├── forum-common-web/                # 全局异常、请求 ID、统一错误；servlet 专用
│   └── forum-common-mq/                 # 事件 DTO、Rabbit 配置
├── forum-api/                           # Feign 客户端 + 内部 DTO
│   └── src/main/java/com/example/api/
│       ├── user/UserClient.java
│       └── dto/...
├── forum-gateway/
├── forum-user-service/
├── forum-content-service/
├── forum-notification-service/
├── forum-media-service/
└── forum-weather-service/
```

约束：

- `forum-common-*` **不要放 `@TableName` 实体**，否则会变成共享数据库模型的反模式；
- `forum-api` 只放内部调用 DTO 和 Feign 接口，不放业务实现；
- 每个服务的 `entity/mapper/service/controller` 独立；
- 网关是 WebFlux/Reactive，只依赖 `forum-common-core` + `forum-common-observability`，**不要依赖 servlet 版的 `common-security`/`common-web`**；网关用独立的 Reactive JWT Filter；
- 业务服务按需依赖 `common-security`、`common-web`、`common-mq`；user/content/notification 需要 MQ，media/weather 不需要；
- 公共模块通过 Spring Boot AutoConfiguration + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 暴露 Bean，**不要依赖 `@SpringBootApplication` 的包扫描**，否则服务扫描范围不可控。

### 2.4 关键链路

**登录：**

```text
前端 → gateway → user-service /api/auth/login
  → MySQL 校验 BCrypt
  → 生成 JWT（Redis 无状态，退出时黑名单）
  → 返回 { token, expire, username, role }
```

**帖子列表：**

```text
前端 → gateway → content-service /api/forum/list-topic
  → Redis 缓存命中则直接返回
  → 未命中：MySQL 查 topic 分页
  → 用本地 db_user_snapshot 批量补 username/avatar
  → 回填缓存 60s
```

**发评论：**

```text
前端 → gateway → content-service /api/forum/add-comment
  → MySQL 插入评论
  → 事务提交后发 RabbitMQ notification.created
  → notification-service 消费并插入 db_notification
  → 前端下次拉 /api/notification/list 看到
```

**头像上传：**

```text
前端 → gateway → media-service /api/image/avatar
  → MinIO 上传
  → media-service 调 user-service /internal/users/{uid}/avatar 绑定
  → user-service 更新 db_account_jwt.avatar 并返回旧头像
  → media-service 删除旧对象
  → 返回 { code:200, data:"/avatar/xxx" }
```

**天气：**

```text
前端 → gateway /api/forum/weather
  → 优先匹配 weather 路由 → weather-service
  → Redis 命中则返回；未命中调 QWeather 并缓存 1h
```

### 2.5 数据所有权

| 数据 | 归属服务 | 其他服务如何获取 |
|---|---|---|
| 账号、密码、邮箱、角色 | user-service | 不直接查；内部 API |
| 个人资料、隐私开关 | user-service | `/internal/users/{uid}/profile`，按隐私过滤 |
| 帖子、评论、类型 | content-service | 只在本服务查 |
| 点赞/收藏 | content-service | 只在本服务查 |
| 通知 | notification-service | 只在本服务查 |
| 图片元数据 | media-service | 只在本服务查 |
| 头像路径 | user-service | content 通过 snapshot/事件获取 |
| 天气 | 无持久化 | Redis |

**作者信息策略（推荐）：**

- content-service 维护 `db_user_snapshot`：
  ```text
  uid, username, avatar, gender, show_gender, updated_at
  ```
  只存列表展示需要的轻量字段，不复制手机号/邮箱等 PII。
- 帖子列表直接 join 本地 snapshot，避免 N+1 Feign。
- 帖子详情/评论作者详情调用 user-service 的 profile API，由 user-service 做隐私过滤；content 侧缓存 60s。
- user-service 在资料/头像/隐私变更后发 `user.profile.changed` 事件；content 更新 snapshot 并清 profile 缓存。
- snapshot 缺失时，content 调 `POST /internal/users/profiles` 批量补齐。

> 备选：snapshot 存全量字段 + 隐私标志，content 自己过滤。性能更好，但复制 PII、隐私逻辑分散，不推荐作为首选。

### 2.6 认证与网关

- `forum-common-security` 提供：
  - `JwtService`：生成、解析、校验、黑名单；
  - `JwtAuthFilter`：Servlet `OncePerRequestFilter`，解析 `Authorization: Bearer`，设置 `request.setAttribute(Const.ATTR_USER_ID, uid)`；
  - `CurrentUser` 参数解析器（可选，保留现有 `@RequestAttribute` 也能工作）。
- 每个业务服务都启用 `JwtAuthFilter`，做**服务内校验**，不信任外部传入的 `X-User-Id`。
- 网关做**前置校验**（Phase 2 再加）：校验签名/过期/黑名单，失败直接返回 401 JSON；成功后仍把原始 `Authorization` 透传，服务再校验一次。
- 网关必须剥离外部请求伪造的 `X-User-Id`、`X-Username`、`X-Internal-Token`。
- 内部接口 `/internal/**` 不出网关；用 `X-Internal-Token` + 网络隔离保护。
- JWT 密钥放环境变量 `FORUM_JWT_SECRET`，至少 32 字节；生产可升级 RSA（user-service 持私钥，其他服务持公钥），本期不强制。
- 网关依赖 `spring-cloud-starter-gateway` + `spring-boot-starter-data-redis-reactive`（用于 `RedisRateLimiter` 和 JWT 黑名单），**不要引入 `spring-boot-starter-web`**，否则会与 WebFlux 冲突。
- 路由使用 `lb://service-name`，需要 `spring-cloud-starter-loadbalancer` 与 Nacos discovery；网关本身也注册到 Nacos。

**各服务认证规则（建议）：**

| 服务 | 放行 | 需要认证 | 内部接口 |
|---|---|---|---|
| user | `/api/auth/**` | `/api/user/**` | `/internal/**` 校验内部 token |
| content | 无 | `/api/forum/**` | `/internal/**` 校验内部 token |
| notification | 无 | `/api/notification/**` | 无 |
| media | `/images/**` | `/api/image/**` | `/internal/**` 校验内部 token |
| weather | 无 | `/api/forum/weather` | 无 |

**路由规则：**

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: weather
          uri: lb://forum-weather-service
          predicates:
            - Path=/api/forum/weather
        - id: user
          uri: lb://forum-user-service
          predicates:
            - Path=/api/auth/**,/api/user/**
        - id: content
          uri: lb://forum-content-service
          predicates:
            - Path=/api/forum/**
        - id: notification
          uri: lb://forum-notification-service
          predicates:
            - Path=/api/notification/**
        - id: media-api
          uri: lb://forum-media-service
          predicates:
            - Path=/api/image/**
        - id: media-images
          uri: lb://forum-media-service
          predicates:
            - Path=/images/**
```

注意：`/api/forum/weather` 必须排在 `/api/forum/**` 之前。

### 2.7 服务间通信与一致性

| 场景 | 方式 | 一致性 |
|---|---|---|
| content 查作者资料 | OpenFeign 同步 | 最终一致，缓存 60s |
| media 绑定头像 | OpenFeign 同步 | 同步成功后再返回；失败补偿删除新文件 |
| content 发通知 | RabbitMQ 异步 | 至少一次；消费端按 `eventId` 幂等 |
| user 发邮件 | RabbitMQ 异步 | 至少一次；重试 + DLQ |
| 注册 | user-service 本地事务 | 账号 + 资料 + 隐私同库事务 |
| 发帖/评论 | content-service 本地事务 | 本地事务 + 提交后发事件 |

原则：

- **不引入 Seata**，不搞跨服务强事务；
- 事件在 `@TransactionalEventListener(phase = AFTER_COMMIT)` 中发送；
- 消费端用 `event_id` 唯一键幂等；
- RabbitMQ 使用 durable queue、publisher confirms、手动 ack、DLQ；
- 失败重试 3 次 + 退避；仍失败进 DLQ，人工/定时补偿；
- 若后续要求“绝不丢事件”，再引入 outbox 表，本期先不做。

### 2.8 配置、注册与部署

- Nacos：
  - 注册中心：`spring.cloud.nacos.discovery.server-addr=${NACOS_ADDR:127.0.0.1:8848}`
  - 配置中心（可选）：`forum-common.yaml`、各服务 `application-{env}.yaml`
  - namespace：`forum-dev`；group：`FORUM`
- 每个服务独立 `application.yml`，共享配置放 Nacos 或环境变量。
- Docker Compose（开发）：
  - MySQL 8、Redis 7、RabbitMQ 3-management、MinIO、Nacos standalone
  - Prometheus、Grafana、Alertmanager
  - 六个服务
- 生产：MySQL 可分实例；Nacos 集群；Prometheus 独立；网关前置 Nginx/TLS。
- 端口规划见 2.2；管理端口只在内网暴露。

**版本基线建议：**

> 保持 Spring Boot 3.1.5，使用 Spring Cloud 2022.0.4 + Spring Cloud Alibaba 2022.0.0.0。  
> Phase 0 先做依赖兼容性验证（Nacos discovery、Gateway、OpenFeign、LoadBalancer）。  
> 如果验证不通过，再整体升级到 Boot 3.2.x + Spring Cloud 2023.0.x + Alibaba 2023.0.x；**不要一边拆服务一边大版本升级**。

---

## 3. 数据库拆分与迁移

### 3.1 Schema 映射

| 新 schema | 表 | 来源 |
|---|---|---|
| `forum_user` | `db_account_jwt`、`db_account_details`、`db_account_privacy` | `study` 对应表 |
| `forum_content` | `db_topic`、`db_topic_comment`、`db_topic_type`、`db_topic_interact_like`、`db_topic_interact_collect`、`db_user_snapshot` | `study` + 新表 |
| `forum_notification` | `db_notification` | `study` |
| `forum_media` | `db_image_store` | `study` |
| 不迁移 | `db_account`、`persistent_logins` | 历史遗留，备份后删除 |

### 3.2 必做 DDL 变更

**1）修复互动表主键**

```sql
ALTER TABLE db_topic_interact_like
  MODIFY uid INT NOT NULL,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (tid, uid);

ALTER TABLE db_topic_interact_collect
  MODIFY uid INT NOT NULL,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (tid, uid);
```

**2）修复 gender 类型**

当前 `tinyblob` 存的是 ASCII `"0"`，先转换再改类型：

```sql
ALTER TABLE db_account_details
  ADD COLUMN gender_new TINYINT NOT NULL DEFAULT 0;

UPDATE db_account_details
SET gender_new = CAST(CONVERT(gender USING utf8mb4) AS UNSIGNED);

ALTER TABLE db_account_details
  DROP COLUMN gender,
  RENAME COLUMN gender_new TO gender;
```

**3）通知表补字段**

```sql
ALTER TABLE db_notification
  MODIFY time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN event_id VARCHAR(64) NULL,
  ADD COLUMN read_at DATETIME NULL,
  ADD UNIQUE KEY uk_event (event_id),
  ADD KEY idx_uid_time (uid, time);
```

对应代码：`Notification.time` 从 `String` 改为 `Date`，`NotificationVO.time` 保持 `Date`；新增 `eventId`、`readAt`。

**4）图片表补主键与元数据**

```sql
ALTER TABLE db_image_store
  ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
  ADD COLUMN content_type VARCHAR(64) NULL,
  ADD COLUMN size BIGINT NULL,
  ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD KEY idx_uid (uid);
```

**5）补业务索引**

```sql
ALTER TABLE db_topic
  ADD KEY idx_type_time (type, time),
  ADD KEY idx_uid (uid),
  ADD KEY idx_top_time (top, time);

ALTER TABLE db_topic_comment
  ADD KEY idx_tid_time (tid, time),
  ADD KEY idx_uid (uid);

ALTER TABLE db_notification
  ADD KEY idx_uid_read (uid, read_at);
```

**6）content 侧用户快照**

```sql
CREATE TABLE db_user_snapshot (
  uid INT NOT NULL,
  username VARCHAR(64) NULL,
  avatar VARCHAR(255) NULL,
  gender TINYINT NULL,
  show_gender TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (uid),
  KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**7）可选：事件 outbox**

如果要求通知不丢，再加：

```sql
CREATE TABLE outbox_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_event_id (event_id),
  KEY idx_status_retry (status, next_retry_at)
);
```

本期建议先不做，用 `AFTER_COMMIT + confirms + DLQ` 即可。

### 3.3 数据迁移步骤

1. **备份**：`mysqldump -uroot -p --single-transaction study > study_backup_20261023.sql`
2. 创建四个 schema：
   ```sql
   CREATE DATABASE forum_user DEFAULT CHARACTER SET utf8mb4;
   CREATE DATABASE forum_content DEFAULT CHARACTER SET utf8mb4;
   CREATE DATABASE forum_notification DEFAULT CHARACTER SET utf8mb4;
   CREATE DATABASE forum_media DEFAULT CHARACTER SET utf8mb4;
   ```
3. 执行各 schema 的 DDL（含上面的修正）。
4. 复制数据（显式列名，避免 `SELECT *`）：
   ```sql
   INSERT INTO forum_user.db_account_jwt
     (id, username, password, email, role, avatar, register_time)
   SELECT id, username, password, email, role, avatar, register_time
   FROM study.db_account_jwt;
   ```
   依次复制 details、privacy、topic_type、topic、comment、interact_like、interact_collect、notification、image_store。
5. 修复 gender、互动主键、通知时间、图片主键。
6. 回填 `db_user_snapshot`：
   ```sql
   INSERT INTO forum_content.db_user_snapshot (uid, username, avatar, gender, show_gender)
   SELECT a.id, a.username, a.avatar,
          CAST(CONVERT(d.gender USING utf8mb4) AS UNSIGNED),
          p.gender
   FROM forum_user.db_account_jwt a
   LEFT JOIN forum_user.db_account_details d ON d.id = a.id
   LEFT JOIN forum_user.db_account_privacy p ON p.id = a.id;
   ```
7. 校验：
   - 每张表行数与 `study` 一致；
   - 用户、帖子、评论抽样比对；
   - `SELECT COUNT(*) FROM db_topic_interact_like` 与迁移前一致；
   - 登录、发帖、评论、点赞、通知、头像、天气逐项回归。
8. 保留 `study` 作为只读备份，确认稳定后再下线。

### 3.4 迁移策略

推荐“**短维护窗口 + 一次性切换**”，不要做双写：

1. 旧单体停止写入（或进入只读模式）；
2. 导出/复制数据；
3. 启动新服务；
4. 网关切流；
5. 回归验证；
6. 失败则回滚网关路由 + 重启旧单体。

当前数据量很小（5 用户、16 帖、18 评论），几分钟窗口足够。生产级双写/CDC 不必要。

---

## 4. API 与前端影响

### 4.1 兼容性原则

- **保留现有所有路径和返回结构**，前端页面不重写；
- 网关监听 `4433`，前端 `baseURL` 不变；
- 登录仍是 `application/x-www-form-urlencoded`；
- 业务返回仍是 `{ code, data, message }`；
- 图片仍是 `/images/**` 二进制流；
- 上传仍是 `multipart/form-data`；
- 错误码保持 200/400/401/403 语义（当前业务错误多用 400 body）。

### 4.2 路径归属

| 现有路径 | 新服务 | 备注 |
|---|---|---|
| `/api/auth/**` | user-service | login/logout/register/reset/ask-code |
| `/api/user/**` | user-service | info/details/privacy/avatar 字段 |
| `/api/forum/weather` | weather-service | 网关优先匹配 |
| `/api/forum/**` | content-service | 其余论坛接口 |
| `/api/notification/**` | notification-service | 改为必须认证 |
| `/api/image/**` | media-service | cache/avatar |
| `/images/**` | media-service | 图片流 |
| `/api/test/**` | 删除 | 无前端使用 |

### 4.3 内部 API

| 方法 | 路径 | 提供方 | 调用方 | 用途 |
|---|---|---|---|---|
| GET | `/internal/users/{uid}/profile?viewerId=` | user | content | 按隐私返回作者资料 |
| POST | `/internal/users/profiles` | user | content | 批量作者资料 |
| PUT | `/internal/users/{uid}/avatar` | user | media | 绑定头像，返回旧头像 |
| GET | `/internal/users/{uid}/exists` | user | content | 可选，校验作者 |
| DELETE | `/internal/media/object?name=` | media | user/content | 可选，清理旧对象 |

内部接口要求：

- 不出网关；
- 校验 `X-Internal-Token`；
- Feign 设置连接/读取超时（如 1s/3s）；
- 只允许幂等 GET/PUT；
- 返回统一的内部 DTO，不返回 `RestBean` 之外的敏感字段。

### 4.4 前端改动

预计只改 2 处：

1. `src/main.js`：
   ```js
   axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:4433'
   ```
2. 新增 `.env.development` / `.env.production`：
   ```text
   VITE_API_BASE_URL=http://localhost:4433
   ```

头像上传路径和返回结构保持不变，`UserSetting.vue` 不需要改。  
如果 media-service 的响应结构改变，才需要同步改 `uploadSuccess`；本方案要求保持 `{code:200,data:"/avatar/xxx"}`。

### 4.5 错误与响应约定

- 401：未登录/令牌过期/黑名单；
- 403：无权限/限流；
- 400：参数校验/业务失败；
- 429：网关限流；
- 503：下游服务不可用；
- 所有 JSON 错误保持 `RestBean` 结构；
- 网关生成 `X-Request-Id`，服务写入 MDC 并回传响应头；
- 日志中不打印密码、验证码、完整邮箱/手机号。

---

## 5. Prometheus 业务监控埋点设计

### 5.1 目标与原则

目标不是“只暴露 JVM 指标”，而是能回答：

- 今天有多少人注册/登录成功/登录失败？
- 帖子、评论、点赞、收藏、通知、上传、天气的成功/失败量是多少？
- 各接口的 QPS、P95/P99、5xx 比例是多少？
- 缓存命中率、MQ 积压、DB 连接池、JVM 是否健康？
- 出问题时能定位到哪个服务、哪个业务动作？

原则：

- 业务指标统一 `forum_` 前缀；
- 标签低基数：只用 `type/result/status/kind/scope`，**不用 uid、tid、email、title、page**；
- 计数器用 `Counter`，耗时用 `Timer`，大小用 `DistributionSummary`，当前状态用 `Gauge`；
- 不在 scrape 时查数据库；用定时任务更新 Gauge；
- 管理端口独立，不经过网关；
- 指标单位统一：秒、字节；计数器以 `_total` 结尾（Micrometer/Prometheus 自动处理）。

### 5.2 依赖与公共配置

每个服务增加：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

如需 `@Timed` 注解，再加 `spring-boot-starter-aop` 并注册 `TimedAspect`；本方案主要用 `MeterRegistry` 编程式埋点，避免 AOP 过度使用。

`application.yml` 公共片段：

```yaml
management:
  server:
    port: 9082          # 每个服务不同
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
      env: ${FORUM_ENV:dev}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms,300ms,1s,3s
    web:
      server:
        max-uri-tags: 50
```

`forum-common-observability` 提供：

- `MetricsConfig`：公共标签、`MeterFilter`、`TimedAspect`（可选）；
- `BusinessMetrics`：统一埋点方法；
- `MetricNames`：常量，避免字符串散落。

### 5.3 自动指标（不用自己写）

Actuator/Micrometer 自动提供：

| 类别 | 指标 |
|---|---|
| HTTP | `http_server_requests_seconds_*`（uri、method、status、outcome、exception） |
| JVM | `jvm_memory_used_bytes`、`jvm_gc_pause_seconds_*`、`jvm_threads_live_threads` |
| 进程 | `process_cpu_usage`、`system_cpu_usage`、`process_uptime_seconds` |
| 连接池 | `hikaricp_connections_*` |
| Tomcat | `tomcat_threads_*`、`tomcat_sessions_*` |
| Redis/Lettuce | `lettuce_command_completion_seconds_*` |
| RabbitMQ | `rabbitmq_*`（listener/publish/connection） |
| Logback | `logback_events_total` |
| Gateway | `spring_cloud_gateway_requests_seconds_*`（如开启） |

这些足够做“服务健康 + 接口 RED”大盘；业务指标需要下面自定义。

### 5.4 业务指标清单

> 代码里用点号命名，Prometheus 会转成下划线；下表“Prometheus 名”是最终看到的名字。计数器自动带 `_total`。

| 服务 | 指标（代码名） | Prometheus 名 | 类型 | 标签 | 含义 |
|---|---|---|---|---|---|
| user | `forum.auth.login` | `forum_auth_login_total` | Counter | `result=success/failure` | 登录次数 |
| user | `forum.auth.register` | `forum_auth_register_total` | Counter | `result` | 注册次数 |
| user | `forum.auth.verify_code` | `forum_auth_verify_code_total` | Counter | `type=register/reset/modify`、`result` | 验证码请求 |
| user | `forum.auth.logout` | `forum_auth_logout_total` | Counter | `result` | 退出登录 |
| user | `forum.auth.token_invalid` | `forum_auth_token_invalid_total` | Counter | `reason=expired/blacklist/signature/malformed` | 无效令牌 |
| user | `forum.auth.dau` | `forum_dau` | Gauge | 无 | 日活（Redis HLL） |
| user | `forum.user.profile_update` | `forum_user_profile_update_total` | Counter | `result` | 资料更新 |
| user | `forum.user.avatar_update` | `forum_user_avatar_update_total` | Counter | `result` | 头像绑定 |
| content | `forum.topic.created` | `forum_topic_created_total` | Counter | `type` | 发帖数 |
| content | `forum.topic.updated` | `forum_topic_updated_total` | Counter | 无 | 改帖数 |
| content | `forum.topic.viewed` | `forum_topic_viewed_total` | Counter | `type` | 详情访问 |
| content | `forum.topic.listed` | `forum_topic_listed_total` | Counter | `type` | 列表请求 |
| content | `forum.comment.created` | `forum_comment_created_total` | Counter | 无 | 评论数 |
| content | `forum.comment.deleted` | `forum_comment_deleted_total` | Counter | 无 | 删除评论 |
| content | `forum.interaction` | `forum_interaction_total` | Counter | `type=like/collect`、`action=add/remove` | 点赞/收藏 |
| content | `forum.topic.cache` | `forum_topic_cache_total` | Counter | `scope=list`、`result=hit/miss` | 列表缓存 |
| content | `forum.topic.create.duration` | `forum_topic_create_duration_seconds` | Timer | `result` | 发帖耗时 |
| content | `forum.comment.create.duration` | `forum_comment_create_duration_seconds` | Timer | `result` | 评论耗时 |
| content | `forum.topic.total` | `forum_topic_total` | Gauge | 无 | 帖子总数 |
| content | `forum.comment.total` | `forum_comment_total` | Gauge | 无 | 评论总数 |
| notification | `forum.notification.created` | `forum_notification_created_total` | Counter | `type` | 通知创建 |
| notification | `forum.notification.deleted` | `forum_notification_deleted_total` | Counter | `scope=single/all` | 通知删除 |
| notification | `forum.notification.pending` | `forum_notification_pending` | Gauge | 无 | 未删除通知数 |
| notification | `forum.notification.consume` | `forum_notification_consume_total` | Counter | `event`、`result` | 事件消费 |
| notification | `forum.notification.consume.duration` | `forum_notification_consume_duration_seconds` | Timer | `result` | 消费耗时 |
| notification | `forum.mail.enqueued` | `forum_mail_enqueued_total` | Counter | `type` | 邮件入队 |
| notification | `forum.mail.sent` | `forum_mail_sent_total` | Counter | `type`、`result` | 邮件发送 |
| notification | `forum.mail.send.duration` | `forum_mail_send_duration_seconds` | Timer | `result` | 邮件耗时 |
| media | `forum.media.upload` | `forum_media_upload_total` | Counter | `kind=cache/avatar`、`result` | 上传次数 |
| media | `forum.media.upload.bytes` | `forum_media_upload_bytes` | DistributionSummary | `kind` | 上传大小 |
| media | `forum.media.fetch` | `forum_media_fetch_total` | Counter | `result` | 图片读取 |
| media | `forum.media.fetch.duration` | `forum_media_fetch_duration_seconds` | Timer | `result` | 图片读取耗时 |
| weather | `forum.weather.request` | `forum_weather_request_total` | Counter | `result=cache/api/failure` | 天气请求 |
| weather | `forum.weather.api.duration` | `forum_weather_api_duration_seconds` | Timer | `result` | 第三方耗时 |
| all | `forum.mq.publish` | `forum_mq_publish_total` | Counter | `event`、`result` | 事件发布 |
| all | `forum.mq.consume` | `forum_mq_consume_total` | Counter | `event`、`result` | 事件消费 |
| gateway | `forum.gateway.auth_rejected` | `forum_gateway_auth_rejected_total` | Counter | `reason` | 网关拒绝令牌 |
| gateway | `forum.gateway.rate_limited` | `forum_gateway_rate_limited_total` | Counter | `route` | 网关限流 |
| all | `forum.rate_limit.blocked` | `forum_rate_limit_blocked_total` | Counter | `scope=post/comment/image/email` | 业务限流 |

标签基数说明：

- `type` 目前 5 种，可控；
- `result` 只用 success/failure；
- `reason` 只用固定枚举；
- `route` 只用网关路由 id；
- `event` 只用固定事件名；
- 禁止把 `tid`、`uid`、`page`、`title`、`email` 作为标签。

### 5.5 埋点位置

| 位置 | 埋点 |
|---|---|
| `user-service` 登录成功/失败处理器 | `forum.auth.login{result}` |
| `AccountServiceImpl.registerEmailAccount` | `forum.auth.register{result}` |
| `AccountServiceImpl.registerEmailVerifyCode` | `forum.auth.verify_code{type,result}` |
| `SecurityConfiguration.onLogoutSuccess` | `forum.auth.logout{result}` |
| `JwtAuthFilter` 解析失败分支 | `forum.auth.token_invalid{reason}` |
| `AccountServiceImpl.modifyEmail/changePassword` | `forum.user.profile_update{result}` |
| `media-service` 绑定头像成功/失败 | `forum.user.avatar_update{result}` |
| `TopicServiceImpl.createTopic` | `forum.topic.created{type}` + `forum.topic.create.duration{result}` |
| `TopicServiceImpl.updateTopic` | `forum.topic.updated` |
| `TopicServiceImpl.getTopic` | `forum.topic.viewed{type}` |
| `TopicServiceImpl.listTopicByPage` | `forum.topic.listed{type}` + `forum.topic.cache{scope=list,result=hit/miss}` |
| `TopicServiceImpl.createComment` | `forum.comment.created` + `forum.comment.create.duration{result}` |
| `TopicServiceImpl.deleteComment` | `forum.comment.deleted` |
| `TopicServiceImpl.interact` | `forum.interaction{type,action}` |
| `NotificationServiceImpl.addNotification` | `forum.notification.created{type}` |
| `NotificationController.delete*` | `forum.notification.deleted{scope}` |
| notification 消费者 | `forum.notification.consume{event,result}` + Timer |
| `MailQueueListener` / 新 mail consumer | `forum.mail.sent{type,result}` + Timer |
| `ImageServiceImpl.uploadImage/uploadAvatar` | `forum.media.upload{kind,result}` + bytes + Timer |
| `ImageServiceImpl.fetchImageFromMinio` | `forum.media.fetch{result}` + Timer |
| `WeatherServiceImpl.fetchWeather` | `forum.weather.request{result}` + `forum.weather.api.duration` |
| 各服务 Rabbit 发送封装 | `forum.mq.publish{event,result}` |
| 网关全局过滤器/限流器 | `forum.gateway.auth_rejected`、`forum.gateway.rate_limited` |

### 5.6 Gauge 与定时任务

Gauge 不要用弱引用临时对象，必须持有 `AtomicLong` 字段。

| Gauge | 更新方式 | 频率 |
|---|---|---|
| `forum.dau` | Redis HyperLogLog `PFADD forum:dau:yyyyMMdd uid`；定时 `PFCOUNT` | 每次认证请求 + 60s |
| `forum.topic.total` | `SELECT COUNT(*) FROM db_topic` | 60s |
| `forum.comment.total` | `SELECT COUNT(*) FROM db_topic_comment` | 60s |
| `forum.notification.pending` | `SELECT COUNT(*) FROM db_notification`（未删除） | 30s |
| `forum.users.total` | `SELECT COUNT(*) FROM db_account_jwt` | 60s |
| MQ 队列深度 | RabbitMQ Prometheus 插件（推荐）或 `AmqpAdmin.getQueueInfo` | 15s |

DAU 说明：

- `PFADD` 在每次通过认证的请求里执行；
- key 按天，TTL 35 天；
- HLL 是近似值，误差约 0.81%，足够业务大盘。

### 5.7 Prometheus 配置

`deploy/prometheus/prometheus.yml`：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/rules/*.yml

scrape_configs:
  - job_name: forum-gateway
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['forum-gateway:9080']
        labels:
          application: forum-gateway

  - job_name: forum-user
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['forum-user-service:9081']
        labels:
          application: forum-user-service

  - job_name: forum-content
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['forum-content-service:9082']
        labels:
          application: forum-content-service

  - job_name: forum-notification
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['forum-notification-service:9083']
        labels:
          application: forum-notification-service

  - job_name: forum-media
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['forum-media-service:9084']
        labels:
          application: forum-media-service

  - job_name: forum-weather
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['forum-weather-service:9085']
        labels:
          application: forum-weather-service

  # 可选：RabbitMQ 插件
  - job_name: rabbitmq
    metrics_path: /metrics
    static_configs:
      - targets: ['rabbitmq:15692']

  # 可选：MinIO
  - job_name: minio
    metrics_path: /minio/v2/metrics/cluster
    static_configs:
      - targets: ['minio:9000']
```

本机非 Docker 运行时，把 target 改成 `host.docker.internal:9081` 或宿主 IP。

### 5.8 Grafana 大盘

建议 3 个 Dashboard：

**1）Forum Overview**

- 服务 UP 状态（`up`）
- 总 QPS、5xx 比例、P95/P99
- 各服务 JVM Heap、CPU、线程
- Hikari 连接池使用/等待
- Redis 命令延迟
- RabbitMQ 队列深度
- 实例列表变量 `$application`

**2）Forum Business**

- 注册成功/失败
- 登录成功/失败
- DAU 趋势
- 发帖数（按 type）
- 评论数
- 点赞/收藏（add/remove）
- 通知创建/删除/待处理
- 图片上传（cache/avatar）与失败率
- 天气 cache/api/failure 比例
- 缓存命中率：`rate(forum_topic_cache_total{result="hit"}[5m]) / rate(forum_topic_cache_total[5m])`

**3）Forum Gateway**

- 各路由 QPS
- 4xx/5xx
- 网关限流次数
- JWT 拒绝次数
- 上游超时

### 5.9 告警规则

`deploy/prometheus/rules/forum.yml` 示例：

```yaml
groups:
  - name: forum-business
    rules:
      - alert: ForumServiceDown
        expr: up{job=~"forum-.*"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Forum service {{ $labels.application }} is down"

      - alert: ForumHigh5xxRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
          /
          sum(rate(http_server_requests_seconds_count[5m])) by (application)
          > 0.05
        for: 5m
        labels:
          severity: critical

      - alert: ForumHighLatencyP95
        expr: |
          histogram_quantile(0.95,
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application)
          ) > 1
        for: 5m
        labels:
          severity: warning

      - alert: ForumLoginFailureSpike
        expr: increase(forum_auth_login_total{result="failure"}[5m]) > 20
        for: 5m
        labels:
          severity: warning

      - alert: ForumNotificationBacklog
        expr: forum_notification_pending > 1000
        for: 10m
        labels:
          severity: warning

      - alert: ForumMediaUploadFailure
        expr: |
          sum(rate(forum_media_upload_total{result="failure"}[5m]))
          /
          sum(rate(forum_media_upload_total[5m])) > 0.1
        for: 5m
        labels:
          severity: warning

      - alert: ForumDbPoolPending
        expr: hikaricp_connections_pending > 0
        for: 5m
        labels:
          severity: warning

      - alert: ForumJvmHeapHigh
        expr: |
          sum(jvm_memory_used_bytes{area="heap"}) by (application)
          /
          sum(jvm_memory_max_bytes{area="heap"}) by (application) > 0.9
        for: 10m
        labels:
          severity: warning
```

阈值先按经验设置，上线一周后根据基线调整。

### 5.10 基数、性能与安全约束

- 不给 uid/tid/email/title/page 打标签；
- HTTP URI 标签限制 50 个；
- 只对 HTTP 和关键业务开 histogram，避免 Timer 过多；
- 不在 `/actuator/prometheus` 里查数据库；
- 管理端口只在内网，不经过网关；
- 指标不包含用户隐私；
- 使用 `MeterFilter` 拒绝未知的高基数标签；
- Gauge 持有强引用；
- 指标命名统一 `forum.` 前缀，单位统一。

### 5.11 监控验收

每个服务：

```bash
curl http://localhost:9082/actuator/prometheus | findstr forum_
```

应能看到：

```text
forum_topic_created_total
forum_comment_created_total
forum_interaction_total
forum_topic_cache_total
forum_auth_login_total
forum_media_upload_total
forum_notification_created_total
```

Prometheus：

- Targets 页面 6 个 job 全部 UP；
- 执行 `forum_topic_created_total` 查询有数据；
- Grafana 面板能按 `$application` 过滤；
- 手动停一个服务，`ForumServiceDown` 1 分钟内触发；
- 手动打一次错误请求，5xx 面板和告警能看到。

---

## 6. 分阶段实施计划

> 原则：每个阶段结束都能启动、能回归、能回滚；不做一次性大爆炸替换。

### Phase 0：基线修复与兼容性验证（1–2 天）

- 修密码判断、互动主键、通知鉴权、明文密钥；
- 清洗 `study.sql`，产出四个 schema 的 DDL；
- 验证 Spring Cloud 2022.0.4 + Alibaba 2022.0.0.0 + Boot 3.1.5 的 Nacos/Gateway/Feign 依赖；
- 备份数据库，打 tag。
- **出口标准**：旧单体仍可运行；SQL 可在空库初始化；P0 bug 修复。

### Phase 1：父工程、common、api、单体模块化（2–3 天）

- 新建 `forum-cloud` 父 POM；
- 创建 `forum-common-*` 子模块、`forum-api`；
- 把 `RestBean/BaseData/Const/JWT/异常/限流/缓存/指标` 按职责抽到对应 common 子模块；
- 现有单体先作为 `forum-monolith` 模块（可选）或直接开始拆；
- 所有服务引入 Actuator/Prometheus 基础依赖；
- **出口标准**：`mvn clean package` 成功；单体行为不变；指标端点可访问。

### Phase 2：网关 + Nacos + 路由切换（1–2 天）

- 新建 `forum-gateway`，监听 4433；
- 旧单体移到 8081，网关路由全部 `/api/**` 到单体；
- 接入 Nacos 注册；
- 网关配置 CORS、全局限流、请求 ID；
- 前端 baseURL 改为环境变量（值仍是 4433）；
- **出口标准**：前端所有功能不变；网关指标可见；Nacos 能看到网关和单体。

### Phase 3：user-service 抽取（2–3 天）

- 迁移账号、资料、隐私、验证码、登录、JWT、邮件事件；
- 建 `forum_user`；
- 实现 `/internal/users/{uid}/profile`、批量 profile、avatar 绑定；
- 旧单体暂时通过 Feign 调 user-service 获取作者信息（过渡）；
- **出口标准**：注册/登录/资料/隐私/改密/头像字段全部通过 user-service；网关路由正确。

### Phase 4：content-service 抽取（3–4 天）

- 迁移帖子、评论、类型、点赞/收藏；
- 建 `forum_content`，修互动主键；
- 建 `db_user_snapshot`，消费 `user.profile.changed`；
- 列表用 snapshot，详情/评论用 user-service profile API；
- 发帖/评论保留 Redis 缓存与限流；
- 发通知事件到 RabbitMQ；
- **出口标准**：论坛列表/详情/发帖/改帖/评论/点赞/收藏全部正常；无跨库 SQL。

### Phase 5：notification-service 抽取（1–2 天）

- 迁 `db_notification`；
- 消费 `notification.created`、`mail.requested`；
- 邮件 SMTP 从 user-service 移到 notification-service；
- 幂等、重试、DLQ；
- **出口标准**：回复通知、邮件验证码、通知列表/删除正常。

### Phase 6：media-service 抽取（1–2 天）

- 迁 MinIO 配置、图片上传/读取、`db_image_store`；
- 头像绑定调用 user-service；
- 旧头像清理；
- **出口标准**：发帖插图、头像上传、`/images/**` 读取正常。

### Phase 7：weather-service 抽取（0.5–1 天）

- 迁 QWeather 集成与 Redis 缓存；
- 网关 weather 路由优先；
- **出口标准**：天气卡片正常，缓存命中率指标可见。

### Phase 8：Prometheus/Grafana/告警（2–3 天）

- 六个服务接入 Actuator/Prometheus；
- 业务指标全部埋点；
- Prometheus + Grafana + Alertmanager Docker Compose；
- 三个 Dashboard、告警规则；
- **出口标准**：指标、面板、告警全部验收。

### Phase 9：Docker Compose、文档、回归（2–3 天）

- 一键启动脚本；
- `README`、架构图、API 文档、运维说明；
- 全量回归 + 简单压测；
- 旧单体归档。
- **出口标准**：新环境按文档可启动；前端无回归；监控可用。

---

## 7. 文件级改造清单

### 7.1 新增

| 路径 | 说明 |
|---|---|
| `forum-cloud/pom.xml` | 父 POM，Spring Boot/Cloud/Alibaba 版本管理 |
| `forum-cloud/forum-common/forum-common-core/**` | 无 servlet 的公共核心 |
| `forum-cloud/forum-common/forum-common-security/**` | Servlet JWT 过滤器、CurrentUser |
| `forum-cloud/forum-common/forum-common-web/**` | 全局异常、请求 ID |
| `forum-cloud/forum-common/forum-common-mq/**` | 事件 DTO、Rabbit 配置 |
| `forum-cloud/forum-common/forum-common-observability/**` | Micrometer/Prometheus 公共配置 |
| `forum-cloud/forum-api/**` | Feign 客户端 + 内部 DTO |
| `forum-cloud/forum-gateway/**` | 网关、路由、CORS、限流、JWT 前置 |
| `forum-cloud/forum-user-service/**` | 认证与用户 |
| `forum-cloud/forum-content-service/**` | 论坛内容 |
| `forum-cloud/forum-notification-service/**` | 通知与邮件 |
| `forum-cloud/forum-media-service/**` | 图片与头像 |
| `forum-cloud/forum-weather-service/**` | 天气 |
| `deploy/docker-compose.yml` | MySQL/Redis/Rabbit/MinIO/Nacos/Prometheus/Grafana |
| `deploy/prometheus/prometheus.yml` | 抓取配置 |
| `deploy/prometheus/rules/forum.yml` | 告警规则 |
| `deploy/grafana/provisioning/**` | 数据源/看板 provisioning |
| `deploy/grafana/dashboards/*.json` | 三个大盘 |
| `deploy/sql/01_user.sql` | 用户库 DDL |
| `deploy/sql/02_content.sql` | 内容库 DDL |
| `deploy/sql/03_notification.sql` | 通知库 DDL |
| `deploy/sql/04_media.sql` | 媒体库 DDL |
| `deploy/sql/05_seed.sql` | 可选种子数据 |
| `docs/architecture.md` | 架构与调用说明 |
| `docs/metrics.md` | 指标字典与埋点规范 |
| `scripts/smoke.ps1` / `smoke.sh` | 冒烟脚本 |

### 7.2 迁移/拆分

| 现有 | 新位置 |
|---|---|
| `config/SecurityConfiguration` | `forum-common-security` + 各服务 SecurityConfig |
| `filter/JWTAuthorizeFilter` | `forum-common-security` |
| `utils/JWTUtils` | `forum-common-security/JwtService` |
| `entity/RestBean`、`BaseData` | `forum-common-core` |
| `utils/Const`、`ControllerUtils` | `forum-common-core` |
| `utils/CacheUtils`、`FlowUtils` | `forum-common-core`（或 common-cache） |
| `controller/exception/ValidationController` | `forum-common-web` |
| `controller/AuthorizeController`、`AccountController` | `user-service` |
| `service/impl/Account*`、`mapper/Account*`、`entity/dto/Account*`、`vo/**/Account*` | `user-service` |
| `listener/MailQueueListener` | `notification-service` |
| `config/RabbitConfiguration` | `forum-common-mq` 或 `notification-service` |
| `controller/ForumController` | `content-service`（weather 方法拆到 weather-service） |
| `service/Topic*`、`mapper/Topic*`、`entity/dto/Topic*`、`vo/**/Topic*`、`vo/response/CommentVO` | `content-service` |
| `controller/NotificationController`、`service/Notification*`、`mapper/NotificationMapper`、`entity/dto/Notification`、`vo/response/NotificationVO` | `notification-service` |
| `controller/ImageController`、`ObjectController`、`service/Image*`、`mapper/ImageStoreMapper`、`entity/dto/StoreImage`、`config/MinioConfiguration` | `media-service` |
| `service/Weather*`、`vo/response/WeatherVO` | `weather-service` |
| `filter/CorsFilter`、`filter/FlowLimitFilter` | `gateway`（限流/CORS） |
| `controller/TestController` | 删除 |
| `BackendJwtApplication` | 替换为各服务 `*Application` |
| `application.yml` | 拆成每服务配置 + Nacos/env |

### 7.3 删除/下线

- `db_account`、`persistent_logins`（备份后）；
- `TestController`；
- 单体 `BackendJwtApplication` 与单体 `application.yml`；
- 旧 `backend-jwt/target` 构建产物（本地清理即可）。

### 7.4 前端

| 文件 | 改动 |
|---|---|
| `src/main.js` | baseURL 改为 `import.meta.env.VITE_API_BASE_URL` |
| `.env.development` / `.env.production` | 新增 |
| 其他页面 | 不改（路径、返回结构保持） |
| `vite.config.js` | 可选：加 dev proxy，避免本地 CORS；不是必须 |

---

## 8. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| Spring Cloud/Alibaba 版本不兼容 | 启动失败、Nacos/Feign 异常 | Phase 0 先做最小依赖验证；锁定 BOM；不与其他升级混做 |
| 跨服务数据一致性 | 作者信息、通知、头像短时不一致 | 事件 + 重试 + 幂等；接受最终一致；关键路径同步调用 |
| 分布式单体 | 服务拆了但共享库/共享表 | 强制 schema 所有权；common 不放 `@TableName`；代码评审 |
| Feign N+1 | 列表页一次请求打爆 user-service | 本地 snapshot + 批量 profile API + 60s 缓存 |
| 鉴权不统一 | 401 格式不一致、越权 | 统一 `common-security`；网关前置校验；内部接口 token |
| 本地资源不足 | 6 服务 + Nacos + 监控启动慢 | Docker Compose + 限制 JVM 内存；开发可只启核心服务 |
| 迁移丢数据 | 用户/帖子丢失 | 备份、行数校验、抽样比对、短维护窗口、回滚脚本 |
| 指标基数爆炸 | Prometheus 内存/性能问题 | 标签白名单；禁止 uid/tid/page；URI 上限 |
| 监控端口暴露 | 信息泄露 | 独立管理端口、内网、防火墙、网关不路由 |
| 前端上传/大文件 | 网关缓冲/超时 | 配置 `spring.codec.max-in-memory-size`、上传超时；必要时媒体直传 |
| 明文密钥 | 凭据泄露 | 环境变量/Nacos 加密；`ca.jks` 移出仓库；轮换 |
| 范围蔓延 | 工期失控 | 冻结新功能；按 Phase 验收；media/weather 可合并 |

---

## 9. 验收标准

### 9.1 功能

- [ ] 现有前端所有页面无需改代码即可访问（baseURL 仍为 4433）。
- [ ] 注册、登录、退出、重置密码、修改邮箱、修改密码正常。
- [ ] 发帖、改帖、列表、筛选、置顶、详情正常。
- [ ] 评论、回复、删除评论正常。
- [ ] 点赞、收藏、收藏夹正常（多用户点赞必须正确）。
- [ ] 通知创建、列表、删除正常。
- [ ] 头像上传、发帖插图、图片读取正常。
- [ ] 天气正常（定位失败回退北京）。
- [ ] 隐私开关生效（手机号/邮箱/微信/QQ/性别）。

### 9.2 架构

- [ ] 六个服务可独立启动、独立部署。
- [ ] 服务全部注册到 Nacos。
- [ ] 网关路由正确，`/api/forum/weather` 不被 content 截获。
- [ ] 每个服务只访问自己的 schema；无跨库 SQL。
- [ ] 内部接口不出网关，`/internal/**` 外部不可访问。
- [ ] 前端只改 baseURL，无业务代码重写。

### 9.3 监控

- [ ] 每个服务 `/actuator/prometheus` 可访问，含 `forum_` 业务指标。
- [ ] Prometheus 六个 target 全部 UP。
- [ ] Grafana 三个 Dashboard 可用。
- [ ] 告警规则加载，停服务/造 5xx 能触发。
- [ ] 指标标签无 uid/tid/email 等高基数字段。
- [ ] 管理端口未暴露到公网/网关。

### 9.4 工程化

- [ ] `mvn clean package` 一键构建。
- [ ] `docker compose up` 一键启动依赖与监控。
- [ ] README 包含架构、启动、端口、迁移、回滚。
- [ ] `deploy/sql` 可在空库初始化。
- [ ] 冒烟脚本覆盖核心链路。
- [ ] 无明文密钥入库。

### 9.5 性能基线（建议）

- 列表缓存命中 P95 < 300ms；
- 详情 P95 < 500ms；
- 发帖/评论 P95 < 800ms；
- 5xx < 1%（正常压测）；
- 100 并发无明显错误；
- JVM Heap 稳定，无持续 Full GC。

---

## 10. 待确认决策

| # | 决策 | 推荐 | 备选 | 影响 |
|---|---|---|---|---|
| D1 | 服务粒度 | 1 网关 + 5 业务服务 | 合并 media/weather 到 content | 服务数量、工期、运维复杂度 |
| D2 | 注册/配置中心 | Nacos | Eureka/Consul/静态配置 | 依赖、学习成本 |
| D3 | 版本 | 保持 Boot 3.1.5 + Spring Cloud 2022.0.4 + Alibaba 2022.0.0.0 | 升级 Boot 3.2/3.3 + Spring Cloud 2023 | 风险、工期 |
| D4 | 数据库 | 开发一个 MySQL + 四 schema；生产可分实例 | 一服务一实例 | 运维、成本 |
| D5 | 鉴权 | 服务内 JWT + 网关前置校验 | 仅网关校验 | 安全、改动量 |
| D6 | API | 保留现有路径，网关 4433 | 重新设计 REST 路径 | 前端改动 |
| D7 | 通知/邮件 | RabbitMQ 事件 | 同步 Feign | 耦合、可靠性 |
| D8 | 监控栈 | Prometheus + Grafana + Alertmanager | 只保留 Prometheus 端点 | 可视化、告警 |
| D9 | 部署 | Docker Compose（开发） | 本机脚本/K8s | 环境、工期 |
| D10 | 迁移窗口 | 短维护窗口一次性切换 | 双写/CDC | 复杂度、风险 |

**请确认或修改以上决策。** 确认后我再开始 Phase 0；在确认前不会修改代码。

---

## 附录 A：建议的父 POM 依赖版本

```xml
<properties>
  <java.version>17</java.version>
  <spring-boot.version>3.1.5</spring-boot.version>
  <spring-cloud.version>2022.0.4</spring-cloud.version>
  <spring-cloud-alibaba.version>2022.0.0.0</spring-cloud-alibaba.version>
</properties>
```

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>${spring-boot.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>${spring-cloud.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-alibaba-dependencies</artifactId>
      <version>${spring-cloud-alibaba.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

> 具体以 Phase 0 的实际依赖树为准；如果 Nacos 客户端与 Boot 3.1.5 冲突，优先调整 Alibaba 版本，不要先动业务代码。

## 附录 B：事件载荷建议

**notification.created**

```json
{
  "eventId": "uuid",
  "recipientUid": 5,
  "actorUid": 6,
  "title": "您有新的帖子回复",
  "content": "test 回复了你发表主题: ...",
  "type": "success",
  "url": "/index/topic-detail/18",
  "occurredAt": "2026-10-23T18:00:00+08:00"
}
```

**mail.requested**

```json
{
  "eventId": "uuid",
  "type": "register",
  "email": "user@example.com",
  "code": "123456",
  "occurredAt": "2026-10-23T18:00:00+08:00"
}
```

**user.profile.changed**

```json
{
  "eventId": "uuid",
  "uid": 5,
  "username": "wzy",
  "avatar": "/avatar/xxx",
  "gender": 0,
  "showGender": true,
  "occurredAt": "2026-10-23T18:00:00+08:00"
}
```

## 附录 C：PromQL 常用查询

```promql
# 各服务 QPS
sum(rate(http_server_requests_seconds_count[5m])) by (application)

# 5xx 比例
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
/
sum(rate(http_server_requests_seconds_count[5m])) by (application)

# P95
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))

# 发帖速率（按类型）
sum(rate(forum_topic_created_total[5m])) by (type)

# 登录失败率
sum(rate(forum_auth_login_total{result="failure"}[5m]))
/
sum(rate(forum_auth_login_total[5m]))

# 缓存命中率
sum(rate(forum_topic_cache_total{result="hit"}[5m]))
/
sum(rate(forum_topic_cache_total[5m]))

# 通知积压
forum_notification_pending

# 图片上传失败率
sum(rate(forum_media_upload_total{result="failure"}[5m]))
/
sum(rate(forum_media_upload_total[5m]))

# JVM Heap 使用率
sum(jvm_memory_used_bytes{area="heap"}) by (application)
/
sum(jvm_memory_max_bytes{area="heap"}) by (application)
```

---

**报告结束。**  
当前未改动任何代码；请先确认第 10 节的决策，我再按 Phase 0 开始实施。
