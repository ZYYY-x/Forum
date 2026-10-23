# Phase 2 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | Spring Cloud Gateway、Nacos 注册配置、网关 JWT/CORS/请求 ID/Redis 限流、单体端口迁移 |
| 状态 | **代码与静态路由联调完成；Nacos 注册运行时待验证** |
| 构建 | `mvn clean package -DskipTests` SUCCESS（10 个 reactor 项目） |
| 运行 | 网关 4433/9080，单体 8081/9081；静态路由联调通过 |
| 未完成 | Nacos 服务端未启动，`lb://forum-monolith` 注册发现未做运行时验证 |

---

## 1. 本次完成内容

### 1.1 新增 `forum-gateway`

```text
forum-gateway/
├── pom.xml
└── src/main/
    ├── java/com/example/gateway/
    │   ├── GatewayApplication.java
    │   ├── config/GatewayRateLimitConfig.java
    │   └── filter/
    │       ├── GatewayJwtFilter.java
    │       └── RequestIdFilter.java
    └── resources/application.yml
```

职责：

- 路由 `/api/**`、`/images/**` 到 `forum-monolith`；
- 公开路径 `/api/auth/**`、`/images/**` 不做 JWT 前置校验；
- 其他路径校验 `Authorization: Bearer <jwt>` 的签名/过期时间；
- CORS；
- `X-Request-Id` 生成/透传；
- 基于 Redis 的 `RequestRateLimiter`，按客户端 IP，50 req/s、burst 100；
- 管理端口 `9080`，不路由 `/actuator/**`。

### 1.2 单体调整

| 改动 | 说明 |
|---|---|
| 端口 | `forum-monolith` 默认从 4433 改为 **8081** |
| Nacos | 增加 `spring-cloud-starter-alibaba-nacos-discovery`，启动类加 `@EnableDiscoveryClient` |
| Redis | 增加 `spring.data.redis` 配置，支持密码/库/超时 |
| CORS | 删除单体 `CorsFilter`，统一由网关处理 |
| 限流 | 删除单体全局 `FlowLimitFilter`，统一由网关处理；业务级 `FlowUtils` 保留 |
| 管理端口 | 保持 `9081` |
| 健康检查 | 关闭 mail/rabbit 健康检查后可正常 UP |

### 1.3 配置变化

- 网关 `server.port=4433`，管理端口 `9080`。
- 单体 `server.port=8081`，管理端口 `9081`。
- 网关路由：
  ```yaml
  uri: "${FORUM_UPSTREAM_URI:lb://forum-monolith}"
  ```
  本地无 Nacos 时用 `FORUM_UPSTREAM_URI=http://localhost:8081`。
- Nacos：
  ```yaml
  spring.cloud.nacos.discovery.server-addr=${NACOS_ADDR:127.0.0.1:8848}
  spring.cloud.nacos.discovery.enabled=${NACOS_ENABLED:true}
  ```
- Redis：
  ```yaml
  spring.data.redis.host=${FORUM_REDIS_HOST:localhost}
  spring.data.redis.port=${FORUM_REDIS_PORT:6379}
  spring.data.redis.password=${FORUM_REDIS_PASSWORD:}
  ```
- 本机 Redis 服务启用了 `requirepass`，联调时通过 `FORUM_REDIS_PASSWORD` 注入，未写入源码。

### 1.4 网关 JWT 说明

- 网关只校验 JWT 签名和过期时间；
- **不查 Redis 黑名单**，黑名单由单体/业务服务的 `JwtAuthFilter` 校验；
- 这样可以避免网关依赖 servlet 版 `forum-common-security`；
- 退出登录后的令牌在单体侧仍会被拦截。

---

## 2. 构建验证

```text
mvn clean package -DskipTests
BUILD SUCCESS

Reactor Summary:
forum-cloud ........................................ SUCCESS
forum-common ....................................... SUCCESS
forum-common-core .................................. SUCCESS
forum-common-security .............................. SUCCESS
forum-common-web ................................... SUCCESS
forum-common-mq .................................... SUCCESS
forum-common-observability ......................... SUCCESS
forum-api .......................................... SUCCESS
forum-gateway ...................................... SUCCESS
forum-monolith ..................................... SUCCESS
```

产物：

```text
forum-cloud/forum-gateway/target/forum-gateway.jar
forum-cloud/forum-monolith/target/forum-monolith.jar
```

---

## 3. 静态路由联调结果

启动方式（无 Nacos）：

```powershell
$env:NACOS_ENABLED = 'false'
$env:FORUM_UPSTREAM_URI = 'http://localhost:8081'
$env:FORUM_REDIS_PASSWORD = '本机 Redis 密码'

java -jar forum-monolith/target/forum-monolith.jar
java -jar forum-gateway/target/forum-gateway.jar
```

结果：

| 检查项 | 结果 |
|---|---|
| 单体 `8081` 启动 | 成功 |
| 网关 `4433` 启动 | 成功 |
| 单体 `/actuator/health` | HTTP 200，`UP` |
| 网关 `/actuator/health` | HTTP 200，`UP` |
| 无 token 访问 `/api/forum/types` | HTTP 401，网关返回 `{"code":401,...}` |
| 有效 JWT 访问 `/api/forum/types` | HTTP 200，返回 5 个帖子类型 |
| 无效 token | HTTP 401，网关返回“登录状态无效” |
| CORS 预检 `OPTIONS` | HTTP 200，返回 `Access-Control-Allow-Origin: http://localhost:5173` |
| `X-Request-Id` | 响应头存在，值如 `401f9440-...` |
| Redis 限流器 | 路由请求正常经过 `RequestRateLimiter`（Redis 已连接） |

说明：

- 有效 JWT 是用测试脚本按 `FORUM_JWT_SECRET` 生成的，未修改数据库用户密码。
- 网关/单体都做了 JWT 校验，属于双层校验。
- 测试时关闭了 mail/rabbit 健康检查；默认未配置邮件密码时 `/actuator/health` 会 DOWN。
- 未做 429 压测；限流器已配置并依赖 Redis，建议 Phase 2 收尾时补一次并发压测。

---

## 4. Nacos 状态

已完成：

- 网关和单体都加入 `spring-cloud-starter-alibaba-nacos-discovery`；
- 两个应用都加 `@EnableDiscoveryClient`；
- 配置了 `NACOS_ADDR`、`NACOS_ENABLED`、namespace；
- 网关默认路由为 `lb://forum-monolith`。

远程环境验证（`<infra-host>`）：

- `8848`：HTTP readiness 返回 `OK`；
- `9848`、`9849`：**不可达**；
- Nacos 2.x 客户端注册需要 gRPC `9848`，启动时报：
  ```text
  Server check fail, please check server <infra-host>, port 9848 is available
  Client not connected, current status: STARTING
  ```
- 因此注册/发现仍未完成，当前使用静态路由模式。

下一步需要：

1. 在 Nacos 主机开放 `9848`、`9849`（Nacos 2.x gRPC/集群端口）；
2. 用 `NACOS_ENABLED=true`、`NACOS_ADDR=<infra-host>:8848` 启动服务；
3. 在 Nacos 控制台确认实例；
4. 通过网关再次访问业务接口。

> 如果 Nacos 暂时不可用，静态路由模式已经可以支撑前端联调。
> Phase 3 已将路由拆分为 `FORUM_USER_URI` / `FORUM_MONOLITH_URI`，最新运行方式见 `PHASE3_RESULT.md`。

---

## 5. 当前运行方式

### 无 Nacos（已验证）

```powershell
$env:NACOS_ENABLED = 'false'
$env:FORUM_UPSTREAM_URI = 'http://localhost:8081'
$env:FORUM_REDIS_PASSWORD = '本机 Redis 密码'

java -jar forum-monolith/target/forum-monolith.jar
java -jar forum-gateway/target/forum-gateway.jar
```

访问入口：

```text
http://localhost:4433
```

### 有 Nacos（待联调）

```powershell
$env:NACOS_ENABLED = 'true'
$env:NACOS_ADDR = '127.0.0.1:8848'
$env:FORUM_UPSTREAM_URI = 'lb://forum-monolith'
$env:FORUM_REDIS_PASSWORD = '本机 Redis 密码'

java -jar forum-monolith/target/forum-monolith.jar
java -jar forum-gateway/target/forum-gateway.jar
```

---

## 6. 变更文件

### 新增

- `forum-cloud/forum-gateway/pom.xml`
- `forum-cloud/forum-gateway/src/main/java/com/example/gateway/GatewayApplication.java`
- `forum-cloud/forum-gateway/src/main/java/com/example/gateway/filter/GatewayJwtFilter.java`
- `forum-cloud/forum-gateway/src/main/java/com/example/gateway/filter/RequestIdFilter.java`
- `forum-cloud/forum-gateway/src/main/java/com/example/gateway/config/GatewayRateLimitConfig.java`
- `forum-cloud/forum-gateway/src/main/resources/application.yml`

### 修改

- `forum-cloud/pom.xml`：增加 `forum-gateway` 模块
- `forum-cloud/forum-monolith/pom.xml`：增加 Nacos discovery
- `forum-cloud/forum-monolith/src/main/resources/application.yml`：8081、Redis、Nacos
- `forum-cloud/forum-monolith/src/main/java/com/example/ForumMonolithApplication.java`：`@EnableDiscoveryClient`
- `forum-cloud/.env.example`、`forum-cloud/README.md`
- `backend-jwt/src/main/resources/application.yml`：补 Redis 密码配置
- `backend-jwt/.env.example`：补 Redis 变量

### 删除

- `forum-cloud/forum-monolith/src/main/java/com/example/filter/CorsFilter.java`
- `forum-cloud/forum-monolith/src/main/java/com/example/filter/FlowLimitFilter.java`

---

## 7. 已知风险 / 技术债

1. **Nacos 运行时未验证**：配置已就绪，但服务端未启动。
2. **JWT 黑名单未在网关校验**：由业务服务校验；如果未来网关单独暴露，需要补 Redis 黑名单检查。
3. **CORS 只允许本地开发源**：`localhost:5173`、`127.0.0.1:5173`、`4173`；生产域名需要加配置。
4. **限流依赖 Redis**：Redis 不可用时网关路由会失败；这是有意的 fail-fast 设计。
5. **split package**：common 与 monolith 仍共享包名，Phase 3 继续整理。
6. **业务指标未埋点**：Phase 8 再做。
7. **邮件健康检查**：未配置邮箱密码时默认 `/actuator/health` 会 DOWN，可通过环境变量关闭或配置真实凭据。

---

## 8. Phase 2 验收清单

- [x] `forum-gateway` 模块可编译、可启动
- [x] 网关静态路由到 `forum-monolith:8081`
- [x] 无 token 请求 401 JSON
- [x] 有效 token 访问业务接口 200
- [x] 无效 token 401
- [x] CORS 预检通过
- [x] `X-Request-Id` 返回
- [x] Redis 限流器接入
- [x] 网关/单体 Actuator 健康检查
- [ ] Nacos 注册与 `lb://` 路由
- [ ] 429 限流压测
- [ ] Phase 3 user-service

---

## 9. 下一步（Phase 3）

Phase 3 将抽取 `forum-user-service`：

1. 新建 `forum-user-service`，迁入账号、资料、隐私、认证、验证码；
2. 建 `forum_user` 数据源；
3. 实现内部 profile / avatar 接口；
4. 内容服务暂时仍由 monolith 承载，通过 Feign 调 user-service；
5. 网关增加 `/api/user/**`、`/api/auth/**` 路由；
6. 验证注册/登录/资料/隐私/改密/头像链路。

如果 Phase 2 结果确认，我继续 Phase 3。
