# forum-cloud

校园论坛微服务改造工程。单体已完成拆分，当前为 **1 个网关 + 5 个业务服务**。

- `forum-gateway`：路由、CORS、请求 ID、JWT 前置校验、Redis 限流；
- `forum-user-service`：认证、账号、资料、隐私；
- `forum-content-service`：帖子、类型、评论、点赞/收藏；
- `forum-notification-service`：站内通知、邮件；
- `forum-media-service`：MinIO 图片/头像；
- `forum-weather-service`：QWeather 天气。

## 模块

| 模块 | 作用 | 端口 |
|---|---|---|
| `forum-common/forum-common-core` | `RestBean`、`BaseData`、`Const`、限流/缓存工具 | - |
| `forum-common/forum-common-security` | JWT、JWT 过滤器、内部 token 过滤器 | - |
| `forum-common/forum-common-web` | 全局异常、Web 公共代码 | - |
| `forum-common/forum-common-mq` | RabbitMQ 队列/转换器公共配置 | - |
| `forum-common/forum-common-observability` | Actuator/Prometheus 公共配置 | - |
| `forum-api` | Feign 客户端与内部 DTO | - |
| `forum-gateway` | 网关 | 4433 / 9080 |
| `forum-user-service` | 用户/认证 | 8082 / 9082 |
| `forum-content-service` | 论坛内容 | 8083 / 9083 |
| `forum-notification-service` | 通知/邮件 | 8084 / 9084 |
| `forum-media-service` | 图片/头像 | 8085 / 9085 |
| `forum-weather-service` | 天气 | 8086 / 9086 |

## 构建

```bash
cd forum-cloud
mvn clean package -DskipTests
```

产物：

```text
forum-gateway/target/forum-gateway.jar
forum-user-service/target/forum-user-service.jar
forum-content-service/target/forum-content-service.jar
forum-notification-service/target/forum-notification-service.jar
forum-media-service/target/forum-media-service.jar
forum-weather-service/target/forum-weather-service.jar
```

## 运行

> 网关占用 `4433`；旧 `backend-jwt` 如需运行请先停止。

### 方式 A：静态路由（无需 Nacos）

需要 Redis、RabbitMQ、MySQL、MinIO。

```powershell
$env:NACOS_ENABLED = 'false'

$env:FORUM_REDIS_HOST = '<infra-host>'
$env:FORUM_REDIS_PASSWORD = '<redis-password>'

$env:FORUM_RABBIT_ADDR = 'localhost'
$env:FORUM_RABBIT_USERNAME = 'guest'
$env:FORUM_RABBIT_PASSWORD = 'guest'

$env:FORUM_MINIO_ENDPOINT = 'http://localhost:9000'
$env:FORUM_MINIO_USERNAME = 'minio'
$env:FORUM_MINIO_PASSWORD = '<minio-password>'

$env:FORUM_MAIL_HOST = 'smtp.163.com'
$env:FORUM_MAIL_PORT = '465'
$env:FORUM_MAIL_USERNAME = '<your-163-email>'
$env:FORUM_MAIL_PASSWORD = '******'

$env:FORUM_USER_URI = 'http://localhost:8082'
$env:FORUM_CONTENT_URI = 'http://localhost:8083'
$env:FORUM_NOTIFICATION_URI = 'http://localhost:8084'
$env:FORUM_MEDIA_URI = 'http://localhost:8085'
$env:FORUM_WEATHER_URI = 'http://localhost:8086'

$env:FORUM_USER_SERVICE_URL = 'http://localhost:8082'
$env:FORUM_INTERNAL_TOKEN = 'dev-internal-token'

java -jar forum-user-service/target/forum-user-service.jar
java -jar forum-content-service/target/forum-content-service.jar
java -jar forum-notification-service/target/forum-notification-service.jar
java -jar forum-media-service/target/forum-media-service.jar
java -jar forum-weather-service/target/forum-weather-service.jar
java -jar forum-gateway/target/forum-gateway.jar
```

### 方式 B：Nacos 服务发现

需要 Nacos 2.x，且开放 `8848`、`9848`、`9849`。

```powershell
$env:NACOS_ENABLED = 'true'
$env:NACOS_ADDR = '<infra-host>:8848'

$env:FORUM_USER_URI = 'lb://forum-user-service'
$env:FORUM_CONTENT_URI = 'lb://forum-content-service'
$env:FORUM_NOTIFICATION_URI = 'lb://forum-notification-service'
$env:FORUM_MEDIA_URI = 'lb://forum-media-service'
$env:FORUM_WEATHER_URI = 'lb://forum-weather-service'

$env:FORUM_USER_SERVICE_URL = ''
$env:FORUM_INTERNAL_TOKEN = 'dev-internal-token'
```

其余 Redis/Rabbit/MinIO/Mail 变量同方式 A，然后启动六个 jar。

## 服务与数据库

| 服务 | 业务端口 | 管理端口 | 数据源 |
|---|---:|---:|---|
| `forum-gateway` | 4433 | 9080 | 无 |
| `forum-user-service` | 8082 | 9082 | `forum_user` |
| `forum-content-service` | 8083 | 9083 | `forum_content` |
| `forum-notification-service` | 8084 | 9084 | `forum_notification` |
| `forum-media-service` | 8085 | 9085 | `forum_media` |
| `forum-weather-service` | 8086 | 9086 | 无 |

## 网关路由

| 路径 | 目标 |
|---|---|
| `/api/auth/**`、`/api/user/**` | `forum-user-service` |
| `/api/forum/weather` | `forum-weather-service` |
| `/api/forum/**` | `forum-content-service` |
| `/api/notification/**` | `forum-notification-service` |
| `/api/image/**`、`/images/**` | `forum-media-service` |

## 消息队列

| 队列 | 生产者 | 消费者 | 用途 |
|---|---|---|---|
| `notification` | content-service | notification-service | 评论回复通知 |
| `mail` | user-service | notification-service | 注册/重置/改邮箱验证码 |

## 监控

六个服务通过独立管理端口暴露 Prometheus：

| 服务 | 管理端口 |
|---|---:|
| `forum-gateway` | 9080 |
| `forum-user-service` | 9082 |
| `forum-content-service` | 9083 |
| `forum-notification-service` | 9084 |
| `forum-media-service` | 9085 |
| `forum-weather-service` | 9086 |

```text
http://localhost:<管理端口>/actuator/prometheus
```

业务指标前缀 `forum_*`，包括登录/注册、发帖/评论/互动、通知/邮件、图片、天气、MQ、JWT 拒绝等；HTTP 指标开启直方图/SLO。

监控编排：

```bash
cd deploy/monitoring
docker compose up -d
```

- Prometheus：<http://localhost:9090>
- Grafana：<http://localhost:3000>（admin/admin）
- 告警规则：`deploy/monitoring/prometheus/rules/forum.yml`
- 看板：`deploy/monitoring/grafana/dashboards/`
- 详细说明：`deploy/monitoring/README.md`

> 管理端口只在内网/本机暴露，不经过网关；标签不包含 uid/tid/email。

## 内部接口

| 方法 | 路径 | 提供方 | 说明 |
|---|---|---|---|
| GET | `/internal/users/{uid}/profile` | user | 按隐私返回用户资料 |
| POST | `/internal/users/profiles` | user | 批量用户资料 |
| PUT | `/internal/users/{uid}/avatar` | user | 绑定头像，返回旧头像 |

- 网关不路由 `/internal/**`；
- 需要 `X-Internal-Token`，默认 `dev-internal-token`。

## 环境变量

参考 `.env.example`。当前环境至少需要：

```text
FORUM_USER_DB_PASSWORD
FORUM_CONTENT_DB_PASSWORD
FORUM_NOTIFICATION_DB_PASSWORD
FORUM_MEDIA_DB_PASSWORD
FORUM_REDIS_HOST / FORUM_REDIS_PORT / FORUM_REDIS_PASSWORD
FORUM_RABBIT_ADDR / FORUM_RABBIT_USERNAME / FORUM_RABBIT_PASSWORD
FORUM_MINIO_ENDPOINT / FORUM_MINIO_USERNAME / FORUM_MINIO_PASSWORD
FORUM_MAIL_HOST / FORUM_MAIL_PORT / FORUM_MAIL_USERNAME / FORUM_MAIL_PASSWORD
FORUM_WEATHER_KEY
FORUM_JWT_SECRET
FORUM_INTERNAL_TOKEN
```

Nacos/路由：

```text
NACOS_ENABLED / NACOS_ADDR
FORUM_USER_URI / FORUM_CONTENT_URI / FORUM_NOTIFICATION_URI / FORUM_MEDIA_URI / FORUM_WEATHER_URI
FORUM_USER_SERVICE_URL
```

## Phase 8 验收点

- [x] 六服务 Actuator/Prometheus 端点
- [x] 公共 `BusinessMetrics`
- [x] 用户/内容/通知/媒体/天气/网关业务指标
- [x] HTTP 直方图/SLO
- [x] Prometheus 配置与告警规则
- [x] Grafana 数据源/看板 JSON
- [x] 指标端点联调
- [ ] Prometheus/Grafana 容器实际启动（Docker Hub 拉取超时）
- [ ] Alertmanager 通知渠道

## 容器 / K8s

每个可运行服务都有多阶段 Dockerfile，内置 SkyWalking Java Agent，构建上下文为 `forum-cloud`：

```bash
docker build -f forum-user-service/Dockerfile -t forum-user-service:latest .
```

前端 Dockerfile 在 `frontend-jwt/Dockerfile`，Nginx 通过 `GATEWAY_UPSTREAM` 反代网关。

- K8s 探针、环境变量、SkyWalking 配置：`../docs/DEPLOYMENT_K8S.md`
- 架构图：`../docs/ARCHITECTURE.md`
- API 文档：`../docs/API.md`

## 技术债

- split package：仍共享 `com.example.*` 包名。
- `forum-common-security` 是 servlet 专用；网关使用独立的 Reactive JWT Filter。
- 网关不校验 JWT 黑名单，由业务服务校验。
- 登录失败保持“HTTP 200 + body.code=401”的兼容行为。
- 通知消费失败记录并 ack，未接 DLQ/重试表。
- 媒体元数据未完整映射 `id/content_type/size/created_at`；头像不写 `db_image_store`。
- 图片响应 Content-Type 固定 `image/jpg`。
- 天气仍假设 QWeather 返回 gzip；真实 key 未配置。
- 当前手动启动六个 jar，尚无 Docker Compose/统一脚本（监控除外）。
- 监控容器因 Docker Hub 网络超时未启动；无 Alertmanager 通知渠道。
- `forum_dau` 每 60 秒刷新；通知 pending 是表行数，不是 RabbitMQ 队列深度。
