# Phase 4 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 抽取 `forum-content-service`、拆分论坛路由、content → user 资料查询、content → monolith 通知写入 |
| 状态 | **已完成并通过四服务联调** |
| 构建 | `mvn clean package -DskipTests` SUCCESS（12 个 reactor 项目） |
| 服务 | gateway 4433/9080，user 8082/9082，content 8083/9083，monolith 8081/9081 |
| 数据源 | user → `forum_user`，content → `forum_content`，monolith → `study` |

---

## 1. 本次完成内容

### 1.1 新增 `forum-content-service`

迁入内容域：

- 帖子：创建、更新、列表、置顶、详情、删除评论；
- 类型：类型列表、类型校验；
- 评论：发表、分页、楼中楼引用、删除；
- 点赞/收藏：Redis 缓冲 + 定时刷库；
- 缓存：`forum:preview:*`；
- 限流：发文 3/小时、评论 5/分钟；
- 通过 Feign 调 user-service 获取作者资料；
- 通过 Feign 调 monolith 内部通知接口；
- 独立数据源 `forum_content`；
- 管理端口 `9083`。

### 1.2 单体进一步瘦身

`forum-monolith` 现在只剩：

- 通知：`NotificationController`、`NotificationService`、`NotificationMapper`；
- 图片：`ImageController`、`ObjectController`、`ImageService`、MinIO；
- 天气：`WeatherController`、`WeatherService`；
- 头像绑定仍通过 Feign 调 user-service；
- 内部通知接口 `POST /internal/notifications`，供 content 调用；
- 23 个 Java 文件（Phase 3 为 39）。

### 1.3 内部通知链路

```text
content-service 发评论
  → POST /internal/notifications（X-Internal-Token）
  → monolith InternalNotificationController
  → NotificationService.addNotification
  → study.db_notification
```

- 通知失败只记录 WARN，不影响评论提交；
- Phase 5 抽 notification-service 后，改为 MQ 事件或指向新服务。

### 1.4 网关路由

```text
/api/auth/**、/api/user/**            → forum-user-service
/api/forum/weather                    → forum-monolith（必须先匹配）
/api/forum/**                         → forum-content-service
/api/notification/**、/api/image/**、/images/** → forum-monolith
```

静态模式新增：

```text
FORUM_CONTENT_URI=http://localhost:8083
FORUM_MONOLITH_SERVICE_URL=http://localhost:8081
```

Nacos 模式：

```text
FORUM_CONTENT_URI=lb://forum-content-service
FORUM_MONOLITH_SERVICE_URL=
```

---

## 2. 构建验证

```text
mvn clean package -DskipTests
BUILD SUCCESS
12 个 reactor 项目全部成功
```

源文件数：

- `forum-content-service`：22 个 Java 文件；
- `forum-monolith`：23 个 Java 文件；
- `forum-user-service`：30 个 Java 文件；
- `forum-gateway`：4 个 Java 文件。

---

## 3. 联调结果

启动四个服务（Nacos `<infra-host>:8848`，Redis `<infra-host>:6379`）：

| 检查项 | 结果 |
|---|---|
| user-service `/actuator/health` | 200 / UP |
| content-service `/actuator/health` | 200 / UP |
| monolith `/actuator/health` | 200 / UP |
| gateway `/actuator/health` | 200 / UP |
| `/api/forum/types` | 200，走 content-service |
| `/api/forum/topic?tid=18` | 200，作者来自 user-service |
| `/api/forum/comments?tid=18&page=0` | 200，批量作者来自 user-service |
| `/api/user/info` | 200，走 user-service |
| `/api/notification/list` | 200，走 monolith |
| 无 token 访问 `/api/forum/types` | 401，网关拦截 |

### 跨服务写链路验证

用临时评论验证：

1. 通过网关 `POST /api/forum/add-comment`，content-service 插入评论 `id=22`；
2. content-service 调 monolith 内部通知接口；
3. `study.db_notification` 新增通知 `id=3`，接收人 uid=5；
4. 用 uid=5 的 JWT 查询 `/api/notification/list`，看到：
   ```json
   {
     "id": 3,
     "title": "您有新的帖子回复",
     "content": "ZYYYx 回复了你发表主题: wzy测试，快去看看吧！",
     "type": "success",
     "url": "/index/topic-detail/18"
   }
   ```
5. 删除评论 22、通知 3；
6. 校验恢复：`forum_content.db_topic_comment` 18 行，`study.db_notification` 0 行。

---

## 4. 环境状态

- Nacos：`8848/9848/9849` 已开放，服务注册正常；
- Redis：`<infra-host>:6379`，密码已通过 `FORUM_REDIS_PASSWORD` 注入；
- MinIO：本机 `9000`，`minio/<minio-password>`；已创建 `study` 桶；
- MySQL：本机 3306，`forum_user` / `forum_content` / `study` 三个库均在用。

> Phase 3 期间创建了 MinIO `study` 桶，并完成了头像上传/读取验证；临时用户和对象已清理。

---

## 5. 变更文件

### 新增

- `forum-cloud/forum-content-service/pom.xml`
- `forum-cloud/forum-content-service/src/main/java/com/example/ContentServiceApplication.java`
- `forum-cloud/forum-content-service/src/main/java/com/example/config/SecurityConfiguration.java`
- `forum-cloud/forum-content-service/src/main/java/com/example/config/WebConfiguration.java`
- `forum-cloud/forum-content-service/src/main/java/com/example/config/FeignClientConfiguration.java`
- `forum-cloud/forum-content-service/src/main/resources/application.yml`
- content 域 18 个类（Controller/Service/Mapper/Entity/VO）
- `forum-cloud/forum-api/src/main/java/com/example/api/notification/NotificationClient.java`
- `forum-cloud/forum-api/src/main/java/com/example/api/dto/NotificationCreateRequest.java`
- `forum-cloud/forum-monolith/src/main/java/com/example/controller/WeatherController.java`
- `forum-cloud/forum-monolith/src/main/java/com/example/controller/InternalNotificationController.java`

### 修改

- `forum-cloud/pom.xml`：增加 content-service
- `forum-cloud/forum-gateway/src/main/resources/application.yml`：增加 weather/content 路由
- `forum-cloud/forum-monolith/pom.xml`：恢复 Feign（头像绑定仍需要 UserClient），保留 forum-api
- `forum-cloud/forum-monolith/src/main/resources/application.yml`：保留 user-service URL（头像绑定）、internal token
- `forum-cloud/forum-content-service` 的 `TopicServiceImpl`：通知改为 `NotificationClient`
- `forum-cloud/README.md`、`.env.example`

### 删除

- 单体中的 content 域 18 个类
- 单体 `ForumController`（天气移入 `WeatherController`）

---

## 6. 已知风险 / 技术债

1. **通知仍在 monolith**：Phase 5 抽 notification-service 后改为事件/MQ。
2. **媒体/天气仍在 monolith**：Phase 6/7 继续拆。
3. **`study` 库仍有旧内容表**：Phase 4 后 content 使用 `forum_content`，`study.db_topic*` 成为历史数据；确认无误后可归档。
4. **`db_user_snapshot` 未启用**：content 当前全部走 Feign profile，列表批量已做，但快照表暂未使用。
5. **split package**：content/user/monolith 仍共享 `com.example.*` 包名。
6. **登录失败 HTTP 200**：保持前端兼容。
7. **JWT expire 字符串**：前端过期判断不严谨，后续统一时间戳。
8. **天气未回归**：`/api/forum/weather` 路由已配置，QWeather key 未配置，未做真实请求。

---

## 7. Phase 4 验收清单

- [x] `forum-content-service` 可编译、可启动
- [x] `forum_content` 数据源
- [x] 帖子/类型/评论/点赞收藏迁移
- [x] content → user profile Feign
- [x] content → monolith 内部通知
- [x] 网关拆分 weather/content/monolith 路由
- [x] Nacos 注册与 `lb://` 路由
- [x] 四服务健康检查
- [x] 列表/详情/评论/通知链路
- [x] 临时评论/通知已清理
- [ ] Phase 5 notification-service
- [ ] Phase 6 media-service
- [ ] Phase 7 weather-service
- [ ] Phase 8 Prometheus 业务指标

---

## 8. 下一步（Phase 5）

Phase 5 计划抽取 `forum-notification-service`：

1. 新建服务，迁入通知表/接口；
2. content 发评论后改为 RabbitMQ `notification.created` 事件；
3. notification-service 消费并写入 `forum_notification`；
4. 邮件发送从 user-service 迁入 notification-service；
5. 网关 `/api/notification/**` 指向新服务；
6. 幂等、重试、DLQ；
7. 回归通知列表/删除/回复通知/邮件验证码。

是否继续 Phase 5？
