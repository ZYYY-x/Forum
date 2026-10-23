# Phase 8 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 六服务 Prometheus 业务埋点、Actuator 指标、Prometheus/Grafana/告警配置 |
| 状态 | **代码与配置已完成，指标端点已联调；监控容器未启动（镜像拉取超时）** |
| 构建 | `mvn clean package -DskipTests` SUCCESS（14 个 reactor 项目） |

---

## 1. 本次完成内容

### 1.1 公共埋点组件

- `forum-common-observability` 新增 `BusinessMetrics`：
  - `increment(name, tags...)`；
  - `Timer.Sample` / `time(...)`；
  - `record(name, amount, tags...)`；
  - `gauge(name, AtomicLong, tags...)`。
- `forum-common-security` 新增 `ActiveUserTracker`：
  - JWT 认证通过后写 Redis HyperLogLog `forum:dau:<date>`；
  - 用于日活近似统计（误差约 0.81%）。
- `MetricsConfig` 保留公共标签 `application`、`env`。

### 1.2 各服务业务指标

#### user-service

- 登录成功/失败、退出、注册、验证码、重置密码；
- 资料修改、改密、头像绑定；
- `forum_users_total`、`forum_dau`。

#### content-service

- 发帖、改帖、详情浏览、列表；
- 列表缓存 hit/miss；
- 评论创建/删除；
- 点赞/收藏；
- MQ 发布；
- `forum_topic_total`、`forum_comment_total`。

#### notification-service

- 通知列表/创建/删除；
- 事件消费成功/失败/重复；
- 邮件发送成功/失败；
- `forum_notification_pending`。

#### media-service

- 缓存图/头像上传成功、失败、限流；
- 上传字节数、上传耗时；
- 图片读取成功/失败、读取耗时。

#### weather-service

- 天气请求 cache/api/failure；
- QWeather API 耗时。

#### gateway

- JWT 缺失/无效拒绝计数：`forum_gateway_auth_rejected_total{reason}`；
- Spring Cloud Gateway 自带请求/路由指标；
- HTTP 指标统一带 `application`、`env` 标签。

### 1.3 HTTP 指标直方图

六个服务统一增加：

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms,300ms,1s,3s
    web:
      server:
        max-uri-tags: 50
```

管理端口保持独立：

```text
gateway 9080 / user 9082 / content 9083 / notification 9084 / media 9085 / weather 9086
```

### 1.4 Prometheus / Grafana / 告警

新增 `deploy/monitoring/`：

- `prometheus/prometheus.yml`：六个 job，抓取各管理端口；
- `prometheus/rules/forum.yml`：
  - 服务不可达；
  - 5xx 比例；
  - P95 延迟；
  - 登录失败/网关拒绝；
  - 通知消费失败；
  - 邮件失败；
  - 天气失败率；
  - 通知积压。
- `grafana/provisioning/`：Prometheus 数据源 + dashboard provider；
- `grafana/dashboards/forum-overview.json`；
- `grafana/dashboards/forum-business.json`；
- `docker-compose.yml`：Prometheus 9090 + Grafana 3000。

---

## 2. 构建与指标联调

```text
mvn clean package -DskipTests
BUILD SUCCESS
14 个 reactor 项目全部成功
```

启动六服务后：

| 检查项 | 结果 |
|---|---|
| 六个健康检查 | 全部 200 |
| 六个 `/actuator/prometheus` | 全部可访问 |
| 无 token 访问 `/api/forum/types` | 401 |
| 错误登录 | 200 + body.code=401 |
| 认证后 `/api/user/info`、`/api/forum/types`、`/api/forum/topic`、`/api/notification/list` | 200 |
| 图片不存在 `/images/avatar/...` | 404 |
| 天气请求 | 200 + body.code=400（无 QWeather key） |

实际抓到的业务指标示例：

```text
# gateway 9080
forum_gateway_auth_rejected_total{reason="missing"} 1.0

# user 9082
forum_auth_login_total{result="failure"} 1.0
forum_users_total 5.0
forum_dau 0.0

# content 9083
forum_topic_viewed_total{result="success",type="1"} 1.0
forum_topic_total 16.0
forum_comment_total 18.0

# notification 9084
forum_notification_listed_total 1.0
forum_notification_pending 0.0

# media 9085
forum_media_fetch_total{result="failure"} 1.0
forum_media_fetch_duration_seconds_count 1.0

# weather 9086
forum_weather_request_total{result="failure"} 1.0
```

> `forum_dau` 由定时任务每 60 秒刷新，联调时刚启动所以为 0；指标本身已注册。

---

## 3. 监控配置验证

- Grafana dashboard JSON 已用 `ConvertFrom-Json` 校验通过；
- `docker compose config` 校验通过；
- 由于本机 Docker Hub 网络超时，`prom/prometheus` / `grafana` 镜像未拉取，Prometheus/Grafana 容器未启动；
- 因此 `promtool check config` 和实际抓取未执行。

启动方式见 `deploy/monitoring/README.md`。

---

## 4. 指标设计约束

- 标签只使用 `type/result/scope/kind/reason` 等低基数字段；
- 不把 `uid/tid/email` 放进标签；
- HTTP URI 标签上限 50；
- 管理端口不经过网关，不暴露公网；
- Grafana 默认密码仅用于本地。

---

## 5. 变更文件

### 新增

- `forum-cloud/forum-common/forum-common-observability/src/main/java/com/example/common/observability/BusinessMetrics.java`
- `forum-cloud/forum-common/forum-common-security/src/main/java/com/example/security/ActiveUserTracker.java`
- `forum-cloud/forum-user-service/src/main/java/com/example/metrics/UserMetrics.java`
- `forum-cloud/forum-content-service/src/main/java/com/example/metrics/ContentMetrics.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/metrics/NotificationMetrics.java`
- `deploy/monitoring/**`

### 修改

- `JWTAuthorizeFilter`：认证通过后记录 DAU；
- `GatewayJwtFilter`：JWT 拒绝指标；
- user 登录/注册/资料/头像埋点；
- content 帖子/评论/互动/缓存/MQ 埋点；
- notification 通知/邮件/MQ 埋点；
- media 上传/读取埋点；
- weather 请求/API 埋点；
- 六个服务 `application.yml` 增加 metrics histogram；
- user/content/notification 开启 `@EnableScheduling`。

---

## 6. 已知风险 / 技术债

1. **监控容器未实际启动**：Docker Hub 拉取超时；配置已就绪。
2. **`forum_dau` 刷新间隔 60s**：瞬时看板可能滞后。
3. **通知 pending 是表行数**：不是 RabbitMQ 队列深度；后续可接 RabbitMQ Prometheus plugin。
4. **未接 Alertmanager**：告警规则在 Prometheus UI 查看，未配置邮件/钉钉通知。
5. **媒体元数据/图片 Content-Type 旧问题仍在**。
6. **天气真实 key 未配置**。
7. **split package** 仍存在。

---

## 7. Phase 8 验收清单

- [x] 六服务 Actuator/Prometheus 端点
- [x] 公共 BusinessMetrics
- [x] 用户/内容/通知/媒体/天气/网关业务指标
- [x] HTTP 直方图/SLO
- [x] 低基数标签约束
- [x] Prometheus 配置
- [x] 告警规则
- [x] Grafana 数据源/看板 JSON
- [x] 指标端点联调
- [x] 构建通过
- [ ] Prometheus/Grafana 容器实际启动
- [ ] `promtool` 校验
- [ ] Alertmanager 通知渠道

---

## 8. 结论

Phase 8 的业务埋点和监控配置已完成，六个服务都能输出 `forum_*` 指标。  
监控容器受本机 Docker Hub 网络限制未启动；网络恢复后按 `deploy/monitoring/README.md` 执行即可。
