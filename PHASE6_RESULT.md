# Phase 6 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 抽取 `forum-media-service`、图片/头像路由拆分、邮件真实发送验证 |
| 状态 | **已完成并通过头像上传/读取、邮件发送联调** |
| 构建 | `mvn clean package -DskipTests` SUCCESS（14 个 reactor 项目） |
| 服务 | gateway 4433/9080，user 8082/9082，content 8083/9083，notification 8084/9084，media 8085/9085，monolith 8081/9081（仅天气） |

---

## 1. 本次完成内容

### 1.1 新增 `forum-media-service`

迁入：

- `ImageController`：`/api/image/cache`、`/api/image/avatar`；
- `ObjectController`：`/images/**` 图片流；
- `ImageService` / `ImageServiceImpl`；
- `MinioConfiguration`；
- `ImageStoreMapper`、`StoreImage`；
- 独立数据源 `forum_media`；
- 头像上传后通过 Feign 调 user-service 绑定；
- 绑定失败时补偿删除新上传对象；
- 独立 JWT 校验、Nacos 注册；
- 管理端口 `9085`。

### 1.2 单体只剩天气

`forum-monolith` 现在只剩：

- `WeatherController`；
- `WeatherService` / `WeatherServiceImpl`；
- `WeatherVO`；
- 安全/JWT、Redis、RestTemplate、Actuator；
- 8 个 Java 文件（Phase 5 为 16）；
- 不再依赖 MySQL/MyBatis/MinIO/Feign/forum-api。

### 1.3 网关路由

```text
/api/auth/**、/api/user/**            → forum-user-service
/api/forum/weather                    → forum-monolith（天气）
/api/forum/**                         → forum-content-service
/api/notification/**                  → forum-notification-service
/api/image/**、/images/**             → forum-media-service
```

静态模式新增：

```text
FORUM_MEDIA_URI=http://localhost:8085
```

Nacos 模式：

```text
FORUM_MEDIA_URI=lb://forum-media-service
```

### 1.4 邮件真实发送

- notification-service 使用环境变量注入 SMTP：

  ```text
  FORUM_MAIL_HOST=smtp.163.com
  FORUM_MAIL_PORT=465
  FORUM_MAIL_USERNAME=<your-163-email>
  FORUM_MAIL_PASSWORD=******（未写入仓库）
  ```

- 通过 `/api/auth/ask-code` 触发 user-service → `mail` 队列 → notification-service → 163 SMTP；
- notification-service 日志出现：

  ```text
  MailQueueListener : 邮件发送成功: type=register, email=<your-163-email>
  ```

- RabbitMQ `mail` 队列消费后为 0，确认邮件已发送。

---

## 2. 构建验证

```text
mvn clean package -DskipTests
BUILD SUCCESS
14 个 reactor 项目全部成功
```

源文件数：

- `forum-media-service`：10 个 Java 文件；
- `forum-monolith`：8 个 Java 文件；
- `forum-notification-service`：10 个；
- `forum-content-service`：22 个；
- `forum-user-service`：30 个；
- `forum-gateway`：4 个。

---

## 3. 联调结果

启动 user / notification / media / gateway 四个服务（content、monolith 本次未启动）。

| 检查项 | 结果 |
|---|---|
| notification-service 健康 | 200 / UP |
| media-service 健康 | 200 / UP |
| Nacos 注册 | media 注册成功，`lb://forum-media-service` 路由可用 |
| 临时用户登录 | 200 |
| 头像上传 `/api/image/avatar` | 200，返回 `/avatar/2bd5...` |
| `/api/user/info` | 200，头像字段已更新 |
| `/images/avatar/...` | 200，`image/jpg`，68 字节 |
| `/api/auth/ask-code` | 200 |
| 邮件日志 | `邮件发送成功: type=register, email=<your-163-email>` |
| `mail` 队列 | 消费后 0 |

清理：

- 临时用户 99 已删除；
- 测试头像对象已从 MinIO `study` 桶删除；
- 未残留测试业务数据。

> 邮件实际发送已验证一次，收件人为配置的 163 邮箱；请查收确认。

---

## 4. 环境状态

- Nacos：`<infra-host>:8848/9848/9849`；
- Redis：`<infra-host>:6379`；
- MinIO：`127.0.0.1:9000`，`minio/<minio-password>`，`study` 桶；
- RabbitMQ：`forum-rabbit-test`，`guest/guest`，`5672/15672`；
- MySQL：`forum_user` / `forum_content` / `forum_notification` / `forum_media` / `study`；
- 邮件：163 SMTP 环境变量注入，未写入源码。

---

## 5. 变更文件

### 新增

- `forum-cloud/forum-media-service/pom.xml`
- `forum-cloud/forum-media-service/src/main/java/com/example/MediaServiceApplication.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/config/SecurityConfiguration.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/config/FeignClientConfiguration.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/config/MinioConfiguration.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/controller/ImageController.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/controller/ObjectController.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/service/ImageService.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/service/impl/ImageServiceImpl.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/mapper/ImageStoreMapper.java`
- `forum-cloud/forum-media-service/src/main/java/com/example/entity/dto/StoreImage.java`
- `forum-cloud/forum-media-service/src/main/resources/application.yml`

### 修改

- `forum-cloud/pom.xml`：增加 media-service
- `forum-cloud/forum-gateway/src/main/resources/application.yml`：图片/头像路由改到 media
- `forum-cloud/forum-monolith/pom.xml`：移除 MySQL/MyBatis/MinIO/Feign/forum-api
- `forum-cloud/forum-monolith/src/main/resources/application.yml`：只保留 Redis/Weather/JWT/Nacos
- `forum-cloud/forum-monolith`：删除图片/头像域
- `forum-cloud/forum-notification-service`：邮件成功日志
- `forum-cloud/README.md`、`.env.example`

### 删除

- monolith 图片/头像域 8 个类（含 MinioConfiguration）
- monolith `FeignClientConfiguration`

---

## 6. 已知风险 / 技术债

1. **天气仍在 monolith**：Phase 7 抽取后单体可下线。
2. **媒体元数据不完整**：`StoreImage` 未映射 `id/content_type/size/created_at`；当前只用于 cache 图片记录，头像不记录。
3. **头像对象未记录到 `db_image_store`**：仅 MinIO 对象 + user-service 字段；后续可统一媒体元数据。
4. **图片读取 Content-Type 固定 image/jpg**：PNG 也返回 image/jpg。
5. **邮件无失败重试/DLQ**：失败只记录 ERROR。
6. **split package**：仍共享 `com.example.*`。
7. **天气未回归**：QWeather key 未配置。

---

## 7. Phase 6 验收清单

- [x] `forum-media-service` 可编译、可启动
- [x] `forum_media` 数据源
- [x] 图片上传/读取迁移
- [x] 头像上传 → user-service 绑定
- [x] 网关图片/头像路由
- [x] Nacos 注册与 `lb://` 路由
- [x] 头像上传/读取联调
- [x] 邮件真实发送
- [x] 测试用户/对象清理
- [ ] Phase 7 weather-service
- [ ] Phase 8 Prometheus 业务指标
- [ ] 单体下线

---

## 8. 下一步（Phase 7）

Phase 7 计划抽取 `forum-weather-service`：

1. 新建服务，迁入 `WeatherController`、`WeatherService`、`WeatherVO`；
2. 独立 Redis 缓存与 QWeather 配置；
3. 网关 `/api/forum/weather` 指向 weather-service；
4. `forum-monolith` 可以正式下线；
5. 回归天气卡片、缓存、定位失败回退。

是否继续 Phase 7？
