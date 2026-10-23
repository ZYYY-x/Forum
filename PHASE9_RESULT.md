# Phase 9 执行结果

| 项目 | 内容 |
|---|---|
| 执行日期 | 2026-10-23 |
| 范围 | 六个后端服务 + 前端 Dockerfile、SkyWalking Agent 内置、架构图/API 文档 |
| 状态 | **Dockerfile 与文档已完成；本地六服务 + 前端保持运行，等待测试** |
| 构建 | Maven 14 个模块 SUCCESS；前端 `npm run build` SUCCESS；Docker 镜像未实际构建（Docker Hub 不可达） |

---

## 1. Dockerfile

### 1.1 后端六个服务

| 服务 | Dockerfile | 业务端口 | 管理端口 |
|---|---|---:|---:|
| gateway | `forum-cloud/forum-gateway/Dockerfile` | 4433 | 9080 |
| user | `forum-cloud/forum-user-service/Dockerfile` | 8082 | 9082 |
| content | `forum-cloud/forum-content-service/Dockerfile` | 8083 | 9083 |
| notification | `forum-cloud/forum-notification-service/Dockerfile` | 8084 | 9084 |
| media | `forum-cloud/forum-media-service/Dockerfile` | 8085 | 9085 |
| weather | `forum-cloud/forum-weather-service/Dockerfile` | 8086 | 9086 |

特性：

- 多阶段构建：`maven:3.9-eclipse-temurin-17` → `eclipse-temurin:17-jre-jammy`；
- 构建上下文为 `forum-cloud`，支持父 POM/common/api；
- 非 root 用户 `forum`；
- 内置 **SkyWalking Java Agent 9.2.0**；
- `HEALTHCHECK` 指向管理端口 `/actuator/health`；
- `EXPOSE` 业务端口 + 管理端口。

### 1.2 前端

`frontend-jwt/Dockerfile`：

- `node:20-alpine` 执行 `npm ci` + `npm run build`；
- `nginxinc/nginx-unprivileged:1.27-alpine` 运行，非 root 101；
- Nginx 反代 `/api`、`/images` 到 `GATEWAY_UPSTREAM`；
- SPA fallback；
- `/healthz` 健康检查；
- 运行时环境变量：

```text
GATEWAY_UPSTREAM=http://forum-gateway:4433
```

### 1.3 SkyWalking

后端镜像默认：

```text
JAVA_TOOL_OPTIONS=-javaagent:/skywalking/skywalking-agent.jar
SW_AGENT_NAME=<service>
SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800
```

K8s 需覆盖：

```text
SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap.<ns>.svc.cluster.local:11800
SW_AGENT_INSTANCE_NAME=<pod-name>
JAVA_OPTS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC
```

注意：不要整体覆盖 `JAVA_TOOL_OPTIONS`，否则会丢掉 `-javaagent`。

Agent 下载地址已验证可访问：

```text
https://archive.apache.org/dist/skywalking/java-agent/9.2.0/apache-skywalking-java-agent-9.2.0.tgz
```

构建时可用 `SKYWALKING_AGENT_BASE_URL` 指向内部镜像。

---

## 2. 文档

- [架构图](docs/ARCHITECTURE.md)：Mermaid 总览、关键链路、K8s/SkyWalking 视图；
- [API 文档](docs/API.md)：认证、用户、论坛、通知、图片、天气、内部接口；
- [Docker / K8s 部署](docs/DEPLOYMENT_K8S.md)：构建命令、探针、环境变量、SkyWalking；
- [监控配置](deploy/monitoring/README.md)：Prometheus/Grafana/告警；
- [文档索引](docs/README.md)。

---

## 3. 验证结果

| 检查项 | 结果 |
|---|---|
| `mvn clean package -DskipTests` | SUCCESS，14 个模块 |
| 前端 `npm run build` | SUCCESS |
| 后端 Dockerfile 语法 | 已去掉远程 syntax 指令，避免构建时强制拉取 dockerfile 前端镜像 |
| Docker 镜像构建 | **未执行**：Docker Hub 不可达，基础镜像无法拉取 |
| SkyWalking Agent URL | HTTP 200，约 31MB |
| Nacos 服务 | 6 个：gateway/user/content/notification/media/weather |
| 六服务健康 | 9080、9082~9086 全部 200 |
| 前端 | `http://localhost:5173` 200，`/api` 代理登录/用户/论坛接口 200 |

---

## 4. 当前运行环境（未关闭）

六个后端服务和前端开发服务器已启动，等待测试：

| 入口 | 地址 |
|---|---|
| 前端 | <http://localhost:5173> |
| 网关 | <http://localhost:4433> |
| 管理端口 | 9080、9082~9086 |

测试账号：

```text
username: <local-test-user>
password: <local-test-password>
```

日志目录：

```text
E:\workdir\forum\logs\running
```

当前进程：

```text
user / content / notification / media / weather / gateway
+ Vite frontend
```

> 这是本地测试账号，生产环境请删除或改密。

---

## 5. 变更文件

### 新增

- `forum-cloud/forum-gateway/Dockerfile`
- `forum-cloud/forum-user-service/Dockerfile`
- `forum-cloud/forum-content-service/Dockerfile`
- `forum-cloud/forum-notification-service/Dockerfile`
- `forum-cloud/forum-media-service/Dockerfile`
- `forum-cloud/forum-weather-service/Dockerfile`
- `forum-cloud/.dockerignore`
- `frontend-jwt/Dockerfile`
- `frontend-jwt/.dockerignore`
- `frontend-jwt/nginx/default.conf.template`
- `docs/ARCHITECTURE.md`
- `docs/API.md`
- `docs/DEPLOYMENT_K8S.md`
- `docs/README.md`

### 修改

- `frontend-jwt/vite.config.js`：本地开发代理 `/api`、`/images` 到 `127.0.0.1:4433`；
- `forum-weather-service` 注释清理；
- 根文档状态。

---

## 6. 已知限制

1. **镜像未实际构建**：Docker Hub 网络超时；Dockerfile 已就绪。
2. **SkyWalking 未实际联调**：当前没有 SkyWalking OAP；镜像内置 Agent 并配置了默认 OAP 地址。
3. **K8s 清单未生成**：按你的要求只做 Dockerfile。
4. **前端镜像依赖 `nginxinc/nginx-unprivileged`**；若集群只允许内部镜像，请同步该基础镜像。
5. **测试账号/环境仍在运行**：方便测试，不是生产状态。
6. **监控容器未启动**：Phase 8 的 Docker Hub 限制仍在。

---

## 7. 下一步

1. 在有 Docker Hub/内部镜像仓库的环境构建并推送 7 个镜像；
2. 部署 SkyWalking OAP/UI，设置 `SW_AGENT_COLLECTOR_BACKEND_SERVICES`；
3. 按 `docs/DEPLOYMENT_K8S.md` 编写 Deployment/Service/Ingress（本阶段按约定未生成）；
4. 验证跨服务链路：gateway → content → user、content → RabbitMQ → notification、media → user；
5. 生产化清理：删除测试账号、关闭开发服务器、替换测试密钥。
