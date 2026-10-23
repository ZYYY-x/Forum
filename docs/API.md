# forum API 文档

- 版本：Phase 9
- 入口：`http://<gateway-host>:4433`（前端 Nginx 会反代 `/api`、`/images`）
- 认证：除公开路径外，需 `Authorization: Bearer <JWT>`
- 公开路径：`/api/auth/**`、`/images/**`
- 响应包络：

```json
{ "code": 200, "data": {}, "message": "请求成功" }
```

`code` 常见值：`200` 成功、`400` 业务/参数错误、`401` 未登录/登录失败、`403` 无权限、`404` 不存在、`500` 服务异常。  
登录失败沿用前端兼容行为：HTTP 200，body `code=401`。

---

## 1. 认证 `/api/auth`

### 1.1 获取邮箱验证码

```http
GET /api/auth/ask-code?email=user@example.com&type=register
```

| 参数 | 位置 | 必填 | 说明 |
|---|---|---|---|
| `email` | query | 是 | 邮箱 |
| `type` | query | 是 | `register` / `reset` / `modify` |

- 同一 IP 60 秒限一次；
- 验证码 6 位，3 分钟有效；
- 交给 notification-service 通过 163 SMTP 发送。

### 1.2 登录

```http
POST /api/auth/login
Content-Type: application/x-www-form-urlencoded

username=alice&password=secret123
```

响应：

```json
{
  "code": 200,
  "data": {
    "id": 4,
    "username": "alice",
    "role": "user",
    "token": "eyJ...",
    "expire": "2026-10-30 21:19:33.704"
  },
  "message": "请求成功"
}
```

### 1.3 退出登录

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

将 JWT 加入 Redis 黑名单。

### 1.4 注册

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "code": "123456",
  "username": "alice",
  "password": "secret123"
}
```

### 1.5 重置密码确认

```http
POST /api/auth/reset-confirm
```

```json
{ "email": "user@example.com", "code": "123456" }
```

### 1.6 重置密码

```http
POST /api/auth/reset-password
```

```json
{ "email": "user@example.com", "code": "123456", "password": "newSecret123" }
```

---

## 2. 用户 `/api/user`

以下均需 JWT。

### 2.1 当前用户

```http
GET /api/user/info
```

```json
{
  "id": 4,
  "username": "alice",
  "email": "user@example.com",
  "role": "user",
  "avatar": "/avatar/xxx",
  "registerTime": "2026-10-01T10:00:00.000+00:00"
}
```

### 2.2 资料

```http
GET /api/user/details
```

### 2.3 保存资料

```http
POST /api/user/save-details
```

```json
{
  "username": "alice",
  "gender": 0,
  "phone": "13800000000",
  "qq": "123456",
  "wx": "alice_wx",
  "desc": "简介"
}
```

### 2.4 修改邮箱

```http
POST /api/user/modify-email
```

```json
{ "email": "new@example.com", "code": "123456" }
```

### 2.5 修改密码

```http
POST /api/user/change-password
```

```json
{ "password": "oldSecret", "new_password": "newSecret" }
```

### 2.6 保存隐私

```http
POST /api/user/save-privacy
```

```json
{ "type": "email", "status": true }
```

`type`：`phone` / `email` / `qq` / `wx` / `gender`。

### 2.7 查询隐私

```http
GET /api/user/privacy
```

---

## 3. 论坛 `/api/forum`

均需 JWT。

### 3.1 类型列表

```http
GET /api/forum/types
```

### 3.2 发帖

```http
POST /api/forum/create-topic
```

```json
{
  "type": 1,
  "title": "标题",
  "content": { "ops": [{ "insert": "正文\n" }] }
}
```

- 标题 1~30 字；
- 正文最大 10000 字符；
- 每人每小时 3 篇。

### 3.3 帖子列表

```http
GET /api/forum/list-topic?page=0&type=0
```

- `page` 从 0 开始；
- `type=0` 表示全部；
- 结果有 60 秒 Redis 缓存。

### 3.4 置顶帖

```http
GET /api/forum/top-topic
```

### 3.5 帖子详情

```http
GET /api/forum/topic?tid=18
```

不存在时返回 `code=404`。

### 3.6 点赞/收藏

```http
GET /api/forum/interact?tid=18&type=like&state=true
```

`type`：`like` / `collect`；`state`：`true` 添加，`false` 取消。

### 3.7 我的收藏

```http
GET /api/forum/collects
```

### 3.8 编辑帖子

```http
POST /api/forum/update-topic
```

```json
{
  "id": 18,
  "type": 1,
  "title": "新标题",
  "content": { "ops": [{ "insert": "新正文\n" }] }
}
```

### 3.9 发表评论

```http
POST /api/forum/add-comment
```

```json
{
  "tid": 18,
  "content": "{\"ops\":[{\"insert\":\"评论内容\\n\"}]}",
  "quote": -1
}
```

- `quote=-1` 表示直接评论帖子；
- `quote=<commentId>` 表示回复某条评论；
- 每人每分钟 5 条。

### 3.10 评论列表

```http
GET /api/forum/comments?tid=18&page=0
```

### 3.11 删除评论

```http
GET /api/forum/delete-comment?id=22
```

只能删除自己的评论。

---

## 4. 通知 `/api/notification`

均需 JWT。

### 4.1 列表

```http
GET /api/notification/list
```

```json
[
  {
    "id": 1,
    "title": "您有新的帖子回复",
    "content": "alice 回复了你发表主题: ...",
    "type": "success",
    "url": "/index/topic-detail/18",
    "time": "2026-10-23T12:00:00.000+00:00"
  }
]
```

### 4.2 删除单条

```http
GET /api/notification/delete?id=1
```

### 4.3 全部删除

```http
GET /api/notification/delete-all
```

---

## 5. 图片 `/api/image` 与 `/images`

### 5.1 上传正文图片

```http
POST /api/image/cache
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=@image.png
```

- 最大 5MB；
- 返回 `data: "/cache/20261023/xxx"`；
- 每人每小时 20 张。

### 5.2 上传头像

```http
POST /api/image/avatar
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=@avatar.png
```

- 最大 1MB；
- media-service 调 user-service 绑定头像；
- 返回 `data: "/avatar/xxx"`。

### 5.3 读取图片

```http
GET /images/avatar/xxx
GET /images/cache/20261023/xxx
```

公开，不需要 JWT。

---

## 6. 天气 `/api/forum/weather`

```http
GET /api/forum/weather?longitude=116.40529&latitude=39.90499
Authorization: Bearer <token>
```

成功：

```json
{
  "code": 200,
  "data": {
    "location": { "name": "北京", "id": "101010100" },
    "now": { "temp": "20", "text": "晴" },
    "hourly": [ ... ]
  },
  "message": "请求成功"
}
```

未配置/获取失败：`code=400`。需要 `FORUM_WEATHER_KEY`。

---

## 7. 内部接口（不经过网关）

请求头必须带：

```text
X-Internal-Token: <FORUM_INTERNAL_TOKEN>
```

### 7.1 单个用户资料

```http
GET /internal/users/{uid}/profile
```

按用户隐私设置过滤字段；不存在返回 404。

### 7.2 批量用户资料

```http
POST /internal/users/profiles
Content-Type: application/json

[1, 2, 3]
```

### 7.3 绑定头像

```http
PUT /internal/users/{uid}/avatar
Content-Type: application/json

{ "avatar": "/avatar/xxx" }
```

返回旧头像：

```json
{ "oldAvatar": "/avatar/old" }
```

---

## 8. 调用示例

```bash
# 登录
TOKEN=$(curl -s -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'username=alice' \
  --data-urlencode 'password=secret123' \
  http://localhost:4433/api/auth/login | jq -r '.data.token')

# 帖子列表
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:4433/api/forum/list-topic?page=0&type=0'

# 上传头像
curl -s -H "Authorization: Bearer $TOKEN" \
  -F 'file=@avatar.png' \
  http://localhost:4433/api/image/avatar
```

---

## 9. 相关文档

- [架构说明](ARCHITECTURE.md)
- [K8s/镜像部署](DEPLOYMENT_K8S.md)
- [监控配置](../deploy/monitoring/README.md)
