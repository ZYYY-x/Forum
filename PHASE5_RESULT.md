# Phase 5 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 抽取 `forum-notification-service`、通知改为 RabbitMQ 事件、邮件迁入通知服务、单体只剩图片/天气 |
| 状态 | **已完成并通过事件链路联调** |
| 构建 | `mvn clean package -DskipTests` SUCCESS（13 个 reactor 项目） |
| 服务 | gateway 4433/9080，user 8082/9082，content 8083/9083，notification 8084/9084，monolith 8081/9081 |
| 数据源 | user → `forum_user`，content → `forum_content`，notification → `forum_notification`，monolith → `study` |

---

## 1. 本次完成内容

### 1.1 新增 `forum-notification-service`

- 站内通知列表、单条删除、全部删除；
- 消费 `notification` 队列，写入 `forum_notification.db_notification`；
- 消费 `mail` 队列，发送注册/重置/改邮箱验证码邮件；
- `event_id` 唯一键实现消费幂等；
- 独立 JWT 校验和 Nacos 注册；
- 管理端口 `9084`；
- 数据库 `forum_notification`。

### 1.2 通知改为 RabbitMQ 事件

```text
content-service 发评论
  → AmqpTemplate.convertAndSend("notification", NotificationCreateRequest)
  → notification-service @RabbitListener(queues = "notification")
  → forum_notification.db_notification
```

- 事件带 `eventId`（UUID），消费端按 `event_id` 去重；
- 通知发送失败只记录 WARN，不影响评论提交；
- 当前消费失败采用“记录并 ack”，避免毒消息无限重投；DLQ/重试表留待后续。

### 1.3 邮件迁入 notification-service

- `MailQueueListener` 从 user-service 移到 notification-service；
- user-service 只保留 `AmqpTemplate`，继续向 `mail` 队列投递验证码；
- `spring-boot-starter-mail` 从 user-service 移除；
- notification-service 负责 SMTP；
- SMTP 发送失败会记录 ERROR，不再向上抛出导致无限重投。

### 1.4 单体进一步瘦身

`forum-monolith` 现在只剩：

- 图片：`ImageController`、`ObjectController`、`ImageService`、MinIO；
- 天气：`WeatherController`、`WeatherService`；
- 头像绑定仍通过 Feign 调 user-service；
- 16 个 Java 文件（Phase 4 为 23）；
- 通知控制器/服务/Mapper/实体/VO 已全部移出；
- `study.db_notification` 成为历史表，当前不再写入。

### 1.5 网关路由

```text
/api/auth/**、/api/user/**            → forum-user-service
/api/forum/weather                    → forum-monolith
/api/forum/**                         → forum-content-service
/api/notification/**                  → forum-notification-service
/api/image/**、/images/**             → forum-monolith
```

静态模式新增：

```text
FORUM_NOTIFICATION_URI=http://localhost:8084
```

Nacos 模式：

```text
FORUM_NOTIFICATION_URI=lb://forum-notification-service
```

---

## 2. 构建验证

```text
mvn clean package -DskipTests
BUILD SUCCESS
13 个 reactor 项目全部成功
```

源文件数：

- `forum-notification-service`：10 个 Java 文件；
- `forum-monolith`：16 个 Java 文件；
- `forum-content-service`：22 个；
- `forum-user-service`：30 个。

---

## 3. 联调结果

测试时启动 user / content / notification / gateway 四个服务（monolith 未启动，Phase 5 通知链路不需要它）。

| 检查项 | 结果 |
|---|---|
| notification-service `/actuator/health` | 200 / UP |
| Nacos 服务列表 | 4 个：notification、content、user、gateway |
| RabbitMQ 连接 | 成功（guest/guest，本地测试容器） |
| 发评论 | content 返回 200 |
| 通知写入 | `forum_notification.db_notification` 新增 id=1 |
| 通知列表（uid=5） | 200，返回“您有新的帖子回复” |
| 删除评论 | 200 |
| 删除通知 | 200 |
| 清理后数据 | content 评论 18 条、notification 0 条 |

通知事件端到端验证通过：

```text
content -> notification 队列 -> notification-service -> forum_notification
```

> 邮件发送已在 Phase 6 使用 163 SMTP 实际验证；详见 `PHASE6_RESULT.md`。

---

## 4. 环境状态

- Nacos：`<infra-host>:8848/9848/9849`，正常；
- Redis：`<infra-host>:6379`，正常；
- MinIO：`127.0.0.1:9000`，`minio/<minio-password>`，`study` 桶已创建；
- MySQL：本机 3306，`forum_user` / `forum_content` / `forum_notification` / `study` 均在用；
- RabbitMQ：为联调启动的本地测试容器：

  ```text
  forum-rabbit-test  rabbitmq:management  0.0.0.0:5672->5672, 0.0.0.0:15672->15672
  ```

  默认账号 `guest/guest`。如你已有正式 RabbitMQ，可替换该容器，并通过
  `FORUM_RABBIT_ADDR / FORUM_RABBIT_USERNAME / FORUM_RABBIT_PASSWORD` 配置。

---

## 5. 变更文件

### 新增

- `forum-cloud/forum-notification-service/pom.xml`
- `forum-cloud/forum-notification-service/src/main/java/com/example/NotificationServiceApplication.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/config/SecurityConfiguration.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/controller/NotificationController.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/listener/NotificationListener.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/listener/MailQueueListener.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/service/NotificationService.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/service/impl/NotificationServiceImpl.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/mapper/NotificationMapper.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/entity/dto/Notification.java`
- `forum-cloud/forum-notification-service/src/main/java/com/example/entity/vo/response/NotificationVO.java`
- `forum-cloud/forum-notification-service/src/main/resources/application.yml`
- `forum-cloud/forum-common/forum-common-mq` 增加 `notification` 队列
- `forum-cloud/forum-api/src/main/java/com/example/api/dto/NotificationCreateRequest.java`

### 修改

- `forum-cloud/pom.xml`：增加 notification-service
- `forum-cloud/forum-content-service`：改为 `AmqpTemplate` 发事件，增加 Rabbit 配置/依赖
- `forum-cloud/forum-user-service`：移除 mail 依赖和 MailQueueListener
- `forum-cloud/forum-monolith`：移除通知域
- `forum-cloud/forum-gateway/src/main/resources/application.yml`：增加 notification 路由
- `forum-cloud/README.md`、`.env.example`

### 删除

- monolith 通知域 7 个类
- user-service `MailQueueListener`
- `forum-api` `NotificationClient`（不再走内部 HTTP 通知）
- monolith `InternalNotificationController`

---

## 6. 已知风险 / 技术债

1. **DLQ/重试未实现**：消费者异常时记录并 ack；生产建议 DLQ 或本地重试表。
2. **邮件实际发送已验证**：Phase 6 使用 163 SMTP 发送成功。
3. **通知仍按用户全量查询**：没有分页/已读状态；`read_at` 字段已预留。
4. **`study.db_notification` 历史表残留**：新通知只写 `forum_notification`，旧表可归档。
5. **媒体/天气仍在 monolith**：Phase 6/7 继续拆。
6. **split package**：仍共享 `com.example.*` 包名。
7. **JWT expire 字符串**：前端过期判断不严谨。
8. **Rabbit 测试容器**：`forum-rabbit-test` 为本地测试用途，生产需替换。

---

## 7. Phase 5 验收清单

- [x] `forum-notification-service` 可编译、可启动
- [x] `forum_notification` 数据源
- [x] 通知 API 迁移
- [x] content → RabbitMQ `notification` 事件
- [x] notification-service 消费并幂等写入
- [x] 邮件监听器迁入 notification-service
- [x] 网关 `/api/notification/**` 路由
- [x] Nacos 注册
- [x] 事件端到端联调
- [x] 测试数据清理
- [x] 邮件实际发送（Phase 6 验证）
- [ ] Phase 6 media-service
- [ ] Phase 7 weather-service
- [ ] Phase 8 Prometheus 业务指标

---

## 8. 下一步（Phase 6）

Phase 6 计划抽取 `forum-media-service`：

1. 新建服务，迁入 MinIO 配置、图片上传/读取、`db_image_store`；
2. `/api/image/**`、`/images/**` 路由到 media-service；
3. 头像上传后由 media-service 调 user-service 绑定；
4. 单体只剩天气；
5. 回归发帖插图、头像上传、图片读取、旧头像清理。

是否继续 Phase 6？
