# Phase 1 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 多模块骨架、common 子模块、api 预留模块、过渡单体、Actuator/Prometheus 基础 |
| 状态 | **已完成** |
| 构建 | `mvn clean package -DskipTests` SUCCESS（9 个 reactor 项目） |
| 运行 | `forum-monolith` 启动成功，业务 4433 / 管理 9081 |
| 未做 | 未接入 Nacos、未创建网关、未拆业务服务、未做业务指标埋点 |

---

## 1. 新增工程结构

```text
forum-cloud/
├── pom.xml                              # 父 POM（Boot 3.1.5 + Spring Cloud 2022.0.4 + Alibaba 2022.0.0.0）
├── .gitignore
├── .env.example
├── README.md
├── forum-common/
│   ├── pom.xml                          # 公共模块父 POM
│   ├── forum-common-core/
│   ├── forum-common-security/
│   ├── forum-common-web/
│   ├── forum-common-mq/
│   └── forum-common-observability/
├── forum-api/                           # Feign/内部 DTO 预留
└── forum-monolith/                      # 过渡单体（当前可运行）
```

### 1.1 模块职责

| 模块 | 放入的内容 |
|---|---|
| `forum-common-core` | `RestBean`、`BaseData`、`Const`、`ControllerUtils`、`CacheUtils`、`FlowUtils` |
| `forum-common-security` | `JWTUtils`、`JWTAuthorizeFilter` |
| `forum-common-web` | `ValidationController` 全局异常 |
| `forum-common-mq` | `RabbitConfiguration`（mail 队列、Jackson 转换器） |
| `forum-common-observability` | `MetricsConfig`（Micrometer 公共标签） |
| `forum-api` | `package-info` 占位，Phase 3 起放 Feign 客户端 |
| `forum-monolith` | 全部业务 Controller/Service/Mapper/DTO、配置、`ForumMonolithApplication` |

### 1.2 迁出单体的文件

共迁出 10 个类：

```text
entity/RestBean.java
entity/BaseData.java
utils/Const.java
utils/ControllerUtils.java
utils/CacheUtils.java
utils/FlowUtils.java
utils/JWTUtils.java
filter/JWTAuthorizeFilter.java
controller/exception/ValidationController.java
config/RabbitConfiguration.java
```

`forum-monolith` 剩余 66 个 Java 源文件，已不再包含上述类。

### 1.3 过渡期包名说明

Phase 1 **保留了原包名**（如 `com.example.entity`、`com.example.utils`、`com.example.filter`、`com.example.config`），只按 Maven 模块拆分。

原因：

- 避免一次性修改几十处 import，降低搬迁风险；
- 保证 `forum-monolith` 与旧 `backend-jwt` 行为一致；
- 当前是 classpath 多 jar，允许跨模块同包名，Spring 组件扫描正常。

代价：

- 存在“split package”（同一个包分布在多个 jar）；
- `forum-common-security` 依赖 servlet，不能被 WebFlux 网关直接依赖；
- 后续 Phase 2/3 抽取服务时，应统一整理为 `com.example.common.*` / `com.example.user.*` 等包名。

这一点是 Phase 1 的**已知技术债**，不是最终形态。

### 1.4 父 POM 的版本基线

- Spring Boot `3.1.5`
- Java `17`
- Spring Cloud `2022.0.4`
- Spring Cloud Alibaba `2022.0.0.0`
- MyBatis-Plus `3.5.3.1`
- fastjson2 `2.0.32`
- java-jwt `4.3.0`
- MinIO `8.3.9`

Spring Cloud / Alibaba BOM 已在父 POM 中导入，为 Phase 2 的 Nacos/Gateway 做准备；但 Phase 1 还没有实际引入 Gateway/Nacos 依赖。

---

## 2. 构建验证

执行：

```bash
cd forum-cloud
mvn clean package -DskipTests
```

结果：

```text
Reactor Summary:
forum-cloud ........................................ SUCCESS
forum-common ....................................... SUCCESS
forum-common-core .................................. SUCCESS
forum-common-security .............................. SUCCESS
forum-common-web ................................... SUCCESS
forum-common-mq .................................... SUCCESS
forum-common-observability ......................... SUCCESS
forum-api .......................................... SUCCESS
forum-monolith ..................................... SUCCESS
BUILD SUCCESS
```

产物：

```text
forum-cloud/forum-monolith/target/forum-monolith.jar
```

`forum-monolith` 编译了 66 个源文件；`ca.jks` 没有被复制到新工程，产物中也不包含它。

---

## 3. 运行验证

启动：

```bash
java -jar forum-cloud/forum-monolith/target/forum-monolith.jar
```

为了在没有 Redis/RabbitMQ/邮件密码的环境下验证 Actuator，测试时加了：

```text
--spring.rabbitmq.listener.simple.auto-startup=false
--management.health.rabbit.enabled=false
--management.health.redis.enabled=false
--management.health.mail.enabled=false
```

结果：

```text
Tomcat started on port(s): 4433 (http)
Tomcat started on port(s): 9081 (http)
Started ForumMonolithApplication
```

健康检查：

```text
GET http://127.0.0.1:9081/actuator/health
HTTP 200
{"status":"UP","groups":["liveness","readiness"]}
```

Prometheus 端点：

```text
GET http://127.0.0.1:9081/actuator/prometheus
HTTP 200
```

指标中已带公共标签：

```text
jvm_memory_used_bytes{application="forum-monolith",area="nonheap",env="dev",...}
```

说明：

- 业务端口 `4433`，管理端口 `9081`；
- 管理端点独立于业务端口；
- 业务指标（`forum_*`）尚未埋点，Phase 8 再做；
- 如果不关闭 `management.health.mail.enabled`，在未配置邮箱密码时 `/actuator/health` 会因 `MailHealthIndicator` 返回 503。这是预期行为，不是启动失败。

---

## 4. 当前可运行方式

```bash
cd forum-cloud
mvn -pl forum-monolith -am spring-boot:run
```

或：

```bash
java -jar forum-monolith/target/forum-monolith.jar
```

环境变量参考 `forum-cloud/.env.example`：

- `FORUM_DB_PASSWORD`
- `FORUM_RABBIT_PASSWORD`
- `FORUM_MINIO_PASSWORD`
- `FORUM_MAIL_USERNAME` / `FORUM_MAIL_PASSWORD`
- `FORUM_WEATHER_KEY`
- `FORUM_JWT_SECRET`

数据源仍是 `study`，没有切换到 `forum_user` / `forum_content` 等新 schema。

---

## 5. 与旧工程的关系

- `backend-jwt` 未改动，仍可作为 Phase 1 回滚/对照。
- `forum-cloud/forum-monolith` 是搬迁后的过渡版本。
- 旧前端仍可指向 `4433` 运行；Phase 1 没有改前端。
- `forum-cloud` 目前不是独立 Git 仓库，没有提交。

---

## 6. 已知问题 / 技术债

1. **split package**：common 模块和 monolith 共享 `com.example.*` 包名，Phase 2/3 需要统一整理。
2. **common-security 是 servlet 专用**：网关不能依赖它，Phase 2 要写独立的 Reactive JWT Filter。
3. **forum-api 为空**：只有 `package-info.java`，Phase 3 抽取 user-service 后再填 Feign 客户端。
4. **没有业务指标**：目前只有 Actuator/JVM/HTTP 自动指标，`forum_*` 业务指标留到 Phase 8。
5. **没有 Nacos/Gateway**：Phase 2 才引入。
6. **邮件健康检查**：未配置邮箱密码时 `/actuator/health` 会 DOWN，需要配置或按环境关闭 mail health。
7. **Maven 安装位置**：本机临时 Maven 已被清理，已在 `C:\Users\wzy\apache-maven-3.9.9` 安装持久版本；如使用其他 Maven 可忽略。

---

## 7. Phase 1 验收清单

- [x] 父 POM + 5 个 common 子模块 + api + monolith
- [x] 公共类迁入 common
- [x] `forum-monolith` 可编译
- [x] `forum-monolith` 可启动
- [x] Actuator `/actuator/health` 可访问
- [x] Prometheus `/actuator/prometheus` 可访问
- [x] 公共标签 `application`、`env` 生效
- [x] 未复制 `ca.jks`
- [ ] Nacos 注册（Phase 2）
- [ ] Gateway 路由（Phase 2）
- [ ] 业务指标埋点（Phase 8）

---

## 8. 下一步（Phase 2）

Phase 2 计划：

1. 新建 `forum-gateway`（WebFlux + Spring Cloud Gateway），业务端口继续用 `4433`；
2. 旧 `forum-monolith` 改到 `8081`，网关路由 `/api/**` 到单体；
3. 启动 Nacos，注册网关与单体；
4. 网关配置 CORS、请求 ID、全局限流；
5. 写独立的 Reactive JWT Filter（不复用 servlet 版）；
6. 验证前端不改代码即可通过网关访问。

是否继续 Phase 2，请确认。
