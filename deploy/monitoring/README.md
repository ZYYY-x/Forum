# Phase 8 监控配置

## 目录

```text
deploy/monitoring/
├─ docker-compose.yml
├─ prometheus/
│  ├─ prometheus.yml
│  └─ rules/forum.yml
└─ grafana/
   ├─ dashboards/
   │  ├─ forum-overview.json
   │  └─ forum-business.json
   └─ provisioning/
      ├─ datasources/prometheus.yml
      └─ dashboards/forum.yml
```

## 启动

先启动六个服务（管理端口 9080、9082~9086），再启动 Prometheus/Grafana：

```bash
cd deploy/monitoring
docker compose up -d
```

- Prometheus：<http://localhost:9090>
- Grafana：<http://localhost:3000>，默认 `admin/admin`
- 告警规则：Prometheus UI → Alerts

> Docker Desktop 使用 `host.docker.internal` 访问宿主机服务；
> Linux 下 compose 已通过 `extra_hosts: host-gateway` 兼容。

## 抓取目标

| Job | 地址 |
|---|---|
| forum-gateway | `host.docker.internal:9080` |
| forum-user-service | `host.docker.internal:9082` |
| forum-content-service | `host.docker.internal:9083` |
| forum-notification-service | `host.docker.internal:9084` |
| forum-media-service | `host.docker.internal:9085` |
| forum-weather-service | `host.docker.internal:9086` |

每个服务只通过独立管理端口暴露 `/actuator/prometheus`，不经过网关。

## 业务指标

### user

- `forum_auth_login_total{result}`
- `forum_auth_logout_total{result}`
- `forum_auth_register_total{result}`
- `forum_auth_verify_code_total{type,result}`
- `forum_auth_reset_password_total{result}`
- `forum_user_profile_update_total{result}`
- `forum_user_password_change_total{result}`
- `forum_user_avatar_update_total{result}`
- `forum_users_total`
- `forum_dau`（Redis HLL，日活近似值）

### content

- `forum_topic_created_total{type,result}`
- `forum_topic_updated_total{result}`
- `forum_topic_viewed_total{type,result}`
- `forum_topic_listed_total{type,result}`
- `forum_topic_cache_total{scope,result}`
- `forum_comment_created_total{result}`
- `forum_comment_deleted_total{result}`
- `forum_interaction_total{type,action}`
- `forum_topic_total`
- `forum_comment_total`
- `forum_mq_publish_total{event,result}`

### notification

- `forum_notification_listed_total`
- `forum_notification_created_total{source}`
- `forum_notification_consumed_total{result}`
- `forum_notification_consume_total{result}`
- `forum_notification_consume_duration_seconds`
- `forum_notification_deleted_total{scope}`
- `forum_notification_pending`
- `forum_mail_sent_total{type,result}`
- `forum_mail_send_duration_seconds`

### media

- `forum_media_upload_total{kind,result}`
- `forum_media_upload_duration_seconds{kind}`
- `forum_media_upload_bytes{kind}`
- `forum_media_fetch_total{result}`
- `forum_media_fetch_duration_seconds`

### weather

- `forum_weather_request_total{result}`
- `forum_weather_api_duration_seconds`

### gateway

- `forum_gateway_auth_rejected_total{reason}`
- Spring Cloud Gateway 自带请求/路由指标
- HTTP 指标：`http_server_requests_seconds_*`

## 告警

`prometheus/rules/forum.yml` 包含：

- 服务不可达；
- 5xx 比例 > 5%；
- P95 > 2s；
- 登录失败/网关拒绝突增；
- 通知消费失败；
- 邮件发送失败；
- 天气失败率 > 20%；
- 通知积压 > 1000。

## 安全约束

- 管理端口只在内网/本机暴露，不经过网关；
- 业务指标标签不包含 uid/tid/email，避免高基数与隐私泄露；
- Grafana 默认密码仅用于本地，生产必须修改；
- Prometheus/Grafana 不建议直接暴露公网。
