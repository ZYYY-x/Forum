# Docker / K8s 部署说明（Phase 9）

本阶段只提供镜像构建文件，不提供 K8s YAML；由部署侧按集群规范编写 Deployment/Service/Ingress。

## 1. 镜像清单

`forum-common/*`、`forum-api` 是库模块，不单独生成镜像；可运行的 6 个后端服务 + 前端各有一个 Dockerfile。

| 组件 | Dockerfile | 业务端口 | 管理端口 |
|---|---|---:|---:|
| frontend | `frontend-jwt/Dockerfile` | 8080 | - |
| gateway | `forum-cloud/forum-gateway/Dockerfile` | 4433 | 9080 |
| user | `forum-cloud/forum-user-service/Dockerfile` | 8082 | 9082 |
| content | `forum-cloud/forum-content-service/Dockerfile` | 8083 | 9083 |
| notification | `forum-cloud/forum-notification-service/Dockerfile` | 8084 | 9084 |
| media | `forum-cloud/forum-media-service/Dockerfile` | 8085 | 9085 |
| weather | `forum-cloud/forum-weather-service/Dockerfile` | 8086 | 9086 |

## 2. 构建

### 后端

所有后端 Dockerfile 使用 `forum-cloud` 作为构建上下文（需要父 POM 和 common/api 模块）：

```bash
cd forum-cloud

docker build -f forum-gateway/Dockerfile -t registry.example.com/forum/forum-gateway:phase9 .
docker build -f forum-user-service/Dockerfile -t registry.example.com/forum/forum-user-service:phase9 .
docker build -f forum-content-service/Dockerfile -t registry.example.com/forum/forum-content-service:phase9 .
docker build -f forum-notification-service/Dockerfile -t registry.example.com/forum/forum-notification-service:phase9 .
docker build -f forum-media-service/Dockerfile -t registry.example.com/forum/forum-media-service:phase9 .
docker build -f forum-weather-service/Dockerfile -t registry.example.com/forum/forum-weather-service:phase9 .
```

### 前端

```bash
cd frontend-jwt
docker build -t registry.example.com/forum/forum-frontend:phase9 .
```

前端 Nginx 通过运行时环境变量 `GATEWAY_UPSTREAM` 反向代理网关：

```text
GATEWAY_UPSTREAM=http://forum-gateway:4433
```

K8s 中建议设置为网关 Service 的集群地址，例如：

```text
http://forum-gateway.<namespace>.svc.cluster.local:4433
```

## 3. SkyWalking 链路追踪

后端镜像已经内置 SkyWalking Java Agent 9.2.0，启动参数：

```text
-javaagent:/skywalking/skywalking-agent.jar
```

关键环境变量：

| 变量 | 说明 | 示例 |
|---|---|---|
| `SW_AGENT_NAME` | 服务名（镜像已按服务设置） | `forum-user-service` |
| `SW_AGENT_COLLECTOR_BACKEND_SERVICES` | OAP gRPC 地址 | `skywalking-oap:11800` |
| `SW_AGENT_INSTANCE_NAME` | 实例名，建议注入 Pod 名 | `user-7d9f...` |
| `JAVA_TOOL_OPTIONS` | 必须保留 `-javaagent` | 镜像默认 |
| `JAVA_OPTS` | JVM 内存/GC 参数 | `-XX:MaxRAMPercentage=75` |

K8s 示例（片段，不是完整清单）：

```yaml
env:
  - name: SW_AGENT_NAME
    value: forum-user-service
  - name: SW_AGENT_COLLECTOR_BACKEND_SERVICES
    value: skywalking-oap.skywalking.svc.cluster.local:11800
  - name: SW_AGENT_INSTANCE_NAME
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
  - name: JAVA_OPTS
    value: "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
```

> `JAVA_TOOL_OPTIONS` 已包含 `-javaagent`，不要用 K8s env 整体覆盖它；需要加 JVM 参数请用 `JAVA_OPTS`。  
> 如果集群统一用 initContainer/volume 挂载 agent，可覆盖 `JAVA_TOOL_OPTIONS`，但要确保 `-javaagent` 路径对应挂载点。

如果启用 `readOnlyRootFilesystem: true`，请给 `/skywalking/logs` 挂载可写 emptyDir，或设置：

```text
SW_LOGGING_OUTPUT=CONSOLE
```

SkyWalking OAP 端口：

```text
11800  gRPC（Java Agent 上报）
12800  HTTP（OAP API）
8080   UI（视部署而定）
```

Agent 下载源：

```text
https://archive.apache.org/dist/skywalking/java-agent/9.2.0/
```

构建时可通过 `--build-arg` 指定版本或内部镜像：

```bash
docker build \
  --build-arg SKYWALKING_AGENT_VERSION=9.2.0 \
  --build-arg SKYWALKING_AGENT_BASE_URL=https://archive.apache.org/dist/skywalking/java-agent \
  -f forum-user-service/Dockerfile \
  -t registry.example.com/forum/forum-user-service:phase9 .
```

## 4. K8s 探针

| 服务 | readiness/liveness path | 端口 |
|---|---|---:|
| frontend | `/healthz` | 8080 |
| gateway | `/actuator/health` | 9080 |
| user | `/actuator/health` | 9082 |
| content | `/actuator/health` | 9083 |
| notification | `/actuator/health` | 9084 |
| media | `/actuator/health` | 9085 |
| weather | `/actuator/health` | 9086 |

建议：

```yaml
readinessProbe:
  httpGet: { path: /actuator/health, port: 9082 }
  initialDelaySeconds: 20
  periodSeconds: 10
livenessProbe:
  httpGet: { path: /actuator/health, port: 9082 }
  initialDelaySeconds: 40
  periodSeconds: 20
```

## 5. 必要配置

### 公共

```text
NACOS_ENABLED=true
NACOS_ADDR=nacos:8848
FORUM_REDIS_HOST / FORUM_REDIS_PORT / FORUM_REDIS_PASSWORD
FORUM_RABBIT_ADDR / FORUM_RABBIT_USERNAME / FORUM_RABBIT_PASSWORD
FORUM_JWT_SECRET
FORUM_INTERNAL_TOKEN
```

### 各服务

```text
# user
FORUM_USER_DB_URL / FORUM_USER_DB_USERNAME / FORUM_USER_DB_PASSWORD
FORUM_MAIL_*（仅 notification 使用）

# content
FORUM_CONTENT_DB_URL / FORUM_CONTENT_DB_USERNAME / FORUM_CONTENT_DB_PASSWORD

# notification
FORUM_NOTIFICATION_DB_URL / FORUM_NOTIFICATION_DB_USERNAME / FORUM_NOTIFICATION_DB_PASSWORD
FORUM_MAIL_HOST / FORUM_MAIL_PORT / FORUM_MAIL_USERNAME / FORUM_MAIL_PASSWORD

# media
FORUM_MEDIA_DB_URL / FORUM_MEDIA_DB_USERNAME / FORUM_MEDIA_DB_PASSWORD
FORUM_MINIO_ENDPOINT / FORUM_MINIO_USERNAME / FORUM_MINIO_PASSWORD

# weather
FORUM_WEATHER_KEY

# gateway 静态路由（Nacos 模式用 lb://）
FORUM_USER_URI
FORUM_CONTENT_URI
FORUM_NOTIFICATION_URI
FORUM_MEDIA_URI
FORUM_WEATHER_URI
```

生产环境用 Secret 注入密码/授权码，不要写进镜像或 Git。

## 6. 本地 Docker 网络提示

如果要在 Docker 内运行并访问宿主机中间件：

```text
FORUM_REDIS_HOST=host.docker.internal
FORUM_RABBIT_ADDR=host.docker.internal
FORUM_MINIO_ENDPOINT=http://host.docker.internal:9000
NACOS_ADDR=host.docker.internal:8848
```

前端：

```text
GATEWAY_UPSTREAM=http://host.docker.internal:4433
```

## 7. 当前环境说明

- 本机 Docker Hub 不可达，Phase 9 未实际构建镜像；
- 前端 `npm run build` 已在本机通过；
- 后端 `mvn clean package -DskipTests` 已通过；
- Dockerfile 的 agent 下载地址为 Apache Archive，构建机需能访问；
- 构建若被网络限制，请把 `SKYWALKING_AGENT_BASE_URL` 指向内部制品库。
