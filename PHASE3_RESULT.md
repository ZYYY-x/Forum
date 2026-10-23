# Phase 3 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 抽取 `forum-user-service`、`forum-api` 内部接口、单体通过 Feign 获取用户资料、网关拆分路由 |
| 状态 | **Phase 3 验收完成：静态路由、Nacos 注册、登录/退出、头像上传均已联调通过** |
| 构建 | `mvn clean package -DskipTests` SUCCESS（11 个 reactor 项目） |
| 运行 | user-service 8082/9082，monolith 8081/9081，gateway 4433/9080 |
| 未完成 | 无（Phase 4 已另行完成） |

---

## 1. 本次完成内容

### 1.1 新增 `forum-user-service`

```text
forum-user-service/
├── pom.xml
└── src/main/
    ├── java/com/example/
    │   ├── UserServiceApplication.java
    │   ├── config/SecurityConfiguration.java
    │   ├── config/WebConfiguration.java
    │   ├── controller/AccountController.java
    │   ├── controller/AuthorizeController.java
    │   ├── controller/InternalUserController.java
    │   ├── filter/InternalTokenFilter.java
    │   ├── entity/dto/Account*.java
    │   ├── entity/vo/request/*.java
    │   ├── entity/vo/response/Account*.java、AuthorizeVO.java
    │   ├── listener/MailQueueListener.java
    │   ├── mapper/Account*.java
    │   └── service/Account*.java、impl/Account*.java
    └── resources/application.yml
```

职责：

- `/api/auth/**`：登录、退出、注册、验证码、重置密码；
- `/api/user/**`：账号信息、资料、隐私、改密、改邮箱；
- `/internal/**`：供 content 调用的 profile / batch profile / avatar 绑定；
- 独立数据源：`forum_user`；
- 独立 JWT 签发与黑名单校验；
- 独立邮件队列监听；
- 独立 Nacos 注册配置；
- 管理端口 `9082`。

### 1.2 新增 `forum-api` 内部接口

| 类 | 作用 |
|---|---|
| `ProfileDTO` | 已按隐私过滤的用户资料 |
| `AvatarBindRequest` / `AvatarBindDTO` | 头像绑定请求/旧头像返回 |
| `UserClient` | OpenFeign 客户端 |

接口：

```text
GET  /internal/users/{uid}/profile
POST /internal/users/profiles
PUT  /internal/users/{uid}/avatar
```

- `UserClient.url` 支持 `${forum.services.user.url:}`：
  - 为空：走 Nacos + LoadBalancer；
  - 静态模式：`http://localhost:8082`。
- 内部接口通过 `X-Internal-Token` 校验；网关不路由 `/internal/**`。

### 1.3 单体调整

`forum-monolith` 不再包含用户域，只保留论坛内容/通知/图片/天气：

- 删除 26 个用户相关类（Controller、Service、Mapper、DTO、VO）；
- 删除 `MailQueueListener`；
- 删除 `forum-common-mq`、`spring-boot-starter-mail`、`spring-boot-starter-amqp` 依赖；
- `SecurityConfiguration` 只保留 JWT 资源校验，不再处理登录；
- `WebConfiguration` 移除 BCrypt；
- 新增 `FeignClientConfiguration`，给内部调用加 `X-Internal-Token`；
- 启动类增加 `@EnableFeignClients(basePackages = "com.example.api")`；
- `TopicServiceImpl`：
  - 列表页批量调 `profiles`，避免 N+1；
  - 详情调 `profile`；
  - 评论页批量调 `profiles`；
  - 发评论时用 profile 的 username；
  - 用户服务不可用时降级为空作者信息并记录 WARN。
- `ImageServiceImpl`：
  - 上传头像后调 `PUT /internal/users/{uid}/avatar`；
  - 返回旧头像并删除旧对象；
  - 绑定失败时补偿删除新上传对象。

### 1.4 网关路由拆分

```text
/api/auth/**、/api/user/**  → forum-user-service
/api/forum/**、/api/notification/**、/api/image/**、/images/** → forum-monolith
```

静态模式：

```text
FORUM_USER_URI=http://localhost:8082
FORUM_MONOLITH_URI=http://localhost:8081
```

Nacos 模式：

```text
FORUM_USER_URI=lb://forum-user-service
FORUM_MONOLITH_URI=lb://forum-monolith
```

### 1.5 当前端口

| 服务 | 业务端口 | 管理端口 | 数据库 |
|---|---:|---:|---|
| `forum-gateway` | 4433 | 9080 | 无 |
| `forum-user-service` | 8082 | 9082 | `forum_user` |
| `forum-monolith` | 8081 | 9081 | `study` |

---

## 2. 构建验证

```text
mvn clean package -DskipTests
BUILD SUCCESS

Reactor:
forum-cloud ........................................ SUCCESS
forum-common ....................................... SUCCESS
forum-common-core .................................. SUCCESS
forum-common-security .............................. SUCCESS
forum-common-web ................................... SUCCESS
forum-common-mq .................................... SUCCESS
forum-common-observability ......................... SUCCESS
forum-api .......................................... SUCCESS
forum-gateway ...................................... SUCCESS
forum-user-service ................................. SUCCESS
forum-monolith ..................................... SUCCESS
```

源文件数：

- `forum-user-service`：31 个 Java 文件；
- `forum-monolith`：39 个 Java 文件（从 64 减少）。

产物：

```text
forum-cloud/forum-user-service/target/forum-user-service.jar
forum-cloud/forum-monolith/target/forum-monolith.jar
forum-cloud/forum-gateway/target/forum-gateway.jar
```

---

## 3. 静态路由联调结果

启动方式：`NACOS_ENABLED=false`，Redis 使用 `<infra-host>:6379`，单体通过 `FORUM_USER_SERVICE_URL=http://localhost:8082` 调 user-service。

| 检查项 | 结果 |
|---|---|
| user-service `8082` 启动 | 成功 |
| monolith `8081` 启动 | 成功 |
| gateway `4433` 启动 | 成功 |
| user-service `/actuator/health` | HTTP 200，UP |
| monolith `/actuator/health` | HTTP 200，UP |
| gateway `/actuator/health` | HTTP 200，UP |
| 直接访问 `/internal/users/4/profile` 无内部 token | HTTP 403 |
| 带 `X-Internal-Token` 访问内部 profile | HTTP 200，返回隐私过滤后的资料 |
| 网关 + JWT 访问 `/api/user/info` | HTTP 200，走 user-service |
| 网关 + JWT 访问 `/api/forum/types` | HTTP 200，走 monolith |
| 网关 + JWT 访问 `/api/forum/topic?tid=18` | HTTP 200，作者信息来自 user-service |
| 网关 + JWT 访问 `/api/forum/comments?tid=18&page=0` | HTTP 200，批量作者资料来自 user-service |
| 网关访问 `/api/auth/login`（错误密码） | 返回 `{"code":401,"message":"用户名或密码错误"}`，路由到 user-service |
| 网关访问 `/api/auth/login`（正确密码） | HTTP 200，返回 JWT、username、expire |
| 登录后用 JWT 访问 `/api/user/info` | HTTP 200，返回测试用户 id=99 |
| 登录后调用 `/api/auth/logout` | HTTP 200 |
| 退出后再次用同一 JWT 访问 `/api/user/info` | body.code=401，Redis 黑名单生效 |
| 头像上传 `/api/image/avatar`（临时用户） | HTTP 200，返回 `/avatar/...` |
| 上传后 `/api/user/info` | 返回新头像路径 |
| 读取 `/images/avatar/...` | HTTP 200，返回图片字节 |

说明：

- Redis 远程连通（`PONG`），user-service 的验证码、限流、JWT 黑名单使用该 Redis。
- 登录失败仍保持原项目“HTTP 200 + body.code=401”的兼容行为，没有改成 HTTP 401，避免前端 `net/index.js` 走 axios catch 后只显示通用错误。
- 登录成功使用临时测试用户 `phase3test` 验证，测试后已删除该用户的账号/资料/隐私记录，没有污染现有数据。
- 头像上传已用临时用户验证：MinIO `study` 桶创建后，上传返回 200，`/api/user/info` 返回新头像路径，`/images/avatar/...` 读取返回 200；测试对象和用户已清理。
- Nacos `9848/9849` 已开放，三个服务已在现有 Nacos 注册，`lb://` 路由验证通过。

---

## 4. Nacos 联调（已解决）

Nacos 容器补上 `9848/9849` 映射后，已确认：

```text
<infra-host>:8848  → 可达，readiness OK
<infra-host>:9848  → 可达
<infra-host>:9849  → 可达
<infra-host>:6379  → 可达，AUTH 后 PONG
```

三个服务注册成功：

```json
{"count":3,"doms":["forum-monolith","forum-gateway","forum-user-service"]}
```

网关通过 `lb://forum-user-service`、`lb://forum-monolith` 访问业务接口均返回 200：

- `/api/user/info` → 200；
- `/api/forum/types` → 200。

使用配置：

```powershell
$env:NACOS_ENABLED = 'true'
$env:NACOS_ADDR = '<infra-host>:8848'
$env:FORUM_USER_URI = 'lb://forum-user-service'
$env:FORUM_MONOLITH_URI = 'lb://forum-monolith'
$env:FORUM_REDIS_HOST = '<infra-host>'
$env:FORUM_REDIS_PASSWORD = '<redis-password>'
$env:FORUM_USER_SERVICE_URL = ''
```

> Nacos 2.5.4 服务端 + 客户端 2.2.1 注册/`lb://` 路由已验证。Phase 4 的 content-service 注册见 `PHASE4_RESULT.md`。

---

## 5. 变更文件

### 新增

- `forum-cloud/forum-user-service/pom.xml`
- `forum-cloud/forum-user-service/src/main/java/com/example/UserServiceApplication.java`
- `forum-cloud/forum-user-service/src/main/java/com/example/config/SecurityConfiguration.java`
- `forum-cloud/forum-user-service/src/main/java/com/example/config/WebConfiguration.java`
- `forum-cloud/forum-user-service/src/main/java/com/example/controller/InternalUserController.java`
- `forum-cloud/forum-user-service/src/main/java/com/example/filter/InternalTokenFilter.java`
- `forum-cloud/forum-user-service/src/main/resources/application.yml`
- 28 个从单体迁入的用户域类
- `forum-cloud/forum-api/src/main/java/com/example/api/dto/*.java`
- `forum-cloud/forum-api/src/main/java/com/example/api/user/UserClient.java`
- `forum-cloud/forum-monolith/src/main/java/com/example/config/FeignClientConfiguration.java`

### 修改

- `forum-cloud/pom.xml`：增加 `forum-user-service`
- `forum-cloud/forum-api/pom.xml`：增加 OpenFeign
- `forum-cloud/forum-gateway/src/main/resources/application.yml`：拆分 user/monolith 路由
- `forum-cloud/forum-monolith/pom.xml`：增加 Feign/LoadBalancer/forum-api，移除 MQ/Mail
- `forum-cloud/forum-monolith/src/main/resources/application.yml`：移除 mail/rabbit，增加 user-service URL/internal token
- `forum-cloud/forum-monolith/src/main/java/com/example/ForumMonolithApplication.java`：`@EnableFeignClients`
- `forum-cloud/forum-monolith/src/main/java/com/example/config/SecurityConfiguration.java`：只保留 JWT 资源校验
- `forum-cloud/forum-monolith/src/main/java/com/example/config/WebConfiguration.java`：移除 BCrypt
- `forum-cloud/forum-monolith/src/main/java/com/example/service/impl/TopicServiceImpl.java`：改为 Feign 获取作者
- `forum-cloud/forum-monolith/src/main/java/com/example/service/impl/ImageServiceImpl.java`：头像绑定改为 Feign
- `forum-cloud/README.md`、`.env.example`
- `PHASE2_RESULT.md` 待补充 Nacos gRPC 结论

### 删除

- 单体中 26 个用户域类
- 单体 `MailQueueListener`
- 单体 `forum-common-mq` / mail / amqp 依赖

---

## 6. 已知风险 / 技术债

1. **登录成功已用临时用户验证**：测试用户已删除；现有账号密码仍未知，不影响代码链路。
2. **头像上传已验证**：MinIO `study` 桶已创建；临时用户/对象已清理。
3. **split package**：user-service 与 monolith 仍共享 `com.example.*` 包名，后续继续整理。
4. **content 已在 Phase 4 抽离**：notification/media/weather 仍在 monolith。
5. **登录失败 HTTP 200**：保持原前端契约，未改状态码。
6. **用户资料降级**：user-service 不可用时 content 使用空作者信息并打 WARN，不会让帖子详情直接 500。
7. **JWT expire 序列化格式**：登录响应里 `expire` 是日期字符串；前端 `net/index.js` 目前用字符串和 `Date` 比较，过期判断不严谨，后续可统一为时间戳或前端 `new Date()` 解析。

---

## 7. Phase 3 验收清单

- [x] `forum-user-service` 可编译、可启动
- [x] `forum_user` 数据源
- [x] 内部 profile / batch profile / avatar 接口
- [x] 内部 token 校验
- [x] 网关拆分 `/api/auth/**`、`/api/user/**`
- [x] 单体通过 Feign 获取作者资料
- [x] 列表批量 profile，避免 N+1
- [x] 评论批量 profile
- [x] 静态路由全链路联调
- [x] 登录成功/退出/黑名单回归（临时用户）
- [x] Nacos 注册与 `lb://` 路由联调
- [x] 现有 Nacos 容器 9848/9849 映射
- [x] 头像上传回归
- [x] Phase 4 抽取 content-service（见 `PHASE4_RESULT.md`）

---

## 8. 下一步

Phase 3 的全部验收项已完成。

Phase 4 已抽取 `forum-content-service`，详见 `PHASE4_RESULT.md`；下一步 Phase 5 计划抽取 `forum-notification-service`。
