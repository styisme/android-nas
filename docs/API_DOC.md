# Android NAS 开发者 API 接口规范

本项目的所有端（Web、手机客户端、TV、Watch、CLI、鸿蒙、苹果、小程序）均通过统一的 HTTP JSON API 与局域网内的服务端通信。此外，为了支持多模态远程控制，另设有一套独立的 WebSocket 实时协议。
以下是完整的接口规范说明（基于 v4.5.0）。

---

## 📡 基础约定

- **Base URL**：`http://<ip>:8080` (或 `https://<ip>:8080` 如果开启了 HTTPS)
- **Content-Type**：
  - 常规请求：`application/x-www-form-urlencoded` 或 `application/json`
  - 文件上传：`multipart/form-data` 或 `application/octet-stream`
- **鉴权方式 (Auth)**：
  - 服务端默认开启账号验证，必须在 HTTP Header 中携带 Basic Auth。
  - Header 格式：`Authorization: Basic Base64(账号:密码)` （默认 `admin:admin`）。
  - 鉴权失败将返回 `401 Unauthorized`。
- **全局安全拦截头 (v4.5.0 新增)**：
  - `X-TOTP-Code`：如果服务端开启了 2FA 且登录账号为 Admin，必须携带此头传入 6 位动态验证码。
  - `X-Captcha`：如果触发了防爆破（Fail2Ban）拦截，前端需请求验证码并在此头中传入计算结果。
- **全局隐藏暗号 (v4.5.0 新增)**：
  - 若服务端开启了“隐藏入口”，所有非 API 请求（如访问网页、静态资源）必须在 URL 后附加 `?s=你的暗号`，否则一律返回 404。
- **防 CC 与防爆破状态码**：
  - 连续 5 次鉴权失败，或单 IP 请求 QPS 超限，将直接返回 `403 Forbidden`（受限于 NanoHTTPD 2.2.0，此处不使用 429）。
  - **IP 白名单**：若开启白名单且当前 IP 不在白名单内，同样返回 `403 Forbidden`。
- **高危 API 局域网锁 (v4.5.0 新增)**：
  - 针对 `/api/shell`、`/api/settings`、`/api/system/wipe` 等敏感接口，若公网 IP 访问且未关闭“局域网锁”，将直接返回 `403 Forbidden`。

---

## 📁 一、 文件与目录管理接口

### 1. 获取目录列表
- **URL**: `/api/list`
- **Method**: `GET`
- **Params**: `path` (String, 必填) - 目标目录的绝对路径，如 `/` 或 `/Movies/`。
- **Response**: `200 OK`
```json
{
  "path": "/Movies/",
  "files": [
    { "name": "Inception.mp4", "dir": false, "size": 1024000 }
  ]
}
```

### 2. 文件下载与流媒体分片
- **URL**: `/api/download`
- **Method**: `GET`
- **Params**: `path` (String, 必填) - 文件的绝对路径。
- **Headers**: 可携带 `Range: bytes=0-1024` 用于视频进度条拖动。
- **Response**:
  - 若携带 Range，返回 `206 Partial Content`，并带有 `Content-Range` 头。
  - 若未携带，返回 `200 OK`，并带有 `Content-Disposition` 触发下载。

### 3. 表单多文件上传 (Web端使用)
- **URL**: `/api/upload`
- **Method**: `POST`
- **Body**: `multipart/form-data` 格式。需包含 `path` 字段（目标目录）与一个或多个 `file` 字段。
- **Response**: `200 OK`，返回字符串 `OK: filename.ext`。

### 4. 客户端流式 PUT 上传
- **URL**: `/*` (任意文件路径，如 `/Movies/new.mp4`)
- **Method**: `PUT`
- **Body**: 纯二进制流 (`application/octet-stream`)。
- **注意**：客户端必须携带准确的 `Content-Length`。

### 5. 大文件秒传与分片上传
- **秒传校验**: `POST /api/upload/check?hash=文件MD5` -> 返回 `SECONDS_UPLOAD` 或 `NEED_UPLOAD`。
- **分片上传**: `POST /api/upload/chunk?hash=MD5&index=序号&total=总数&name=文件名&path=目标目录` -> Body 为纯二进制分片。

### 6. 多选流式打包下载
- **URL**: `/api/download/zip`
- **Method**: `POST`
- **Body**: 需包含 `paths` 字段，多个路径用 `|` 分隔（如 `/a.txt|/b.jpg`）。
- **Response**: `200 OK`，实时返回 `application/zip` 数据流。

### 7. 获取文本预览
- **URL**: `/api/preview` (GET)，Params: `path` (String, 必填)。限制 2MB 以内。

### 8. 新建文件夹 / 重命名 / 删除 / 保存
- **新建**: `POST /api/mkdir` (Body: `path=/当前目录&name=新名`)
- **重命名**: `POST /api/rename` (Body: `path=/旧路径&newName=新名字`)
- **删除**: `POST /api/delete` (Body: `path=/目标路径`)
- **保存**: `POST /api/save` (Body: `path=/路径&content=新内容`，自动生成 `.bak`)

### 9. 在线解压 ZIP
- **URL**: `/api/unzip` (POST)，Body: `path=/目标zip文件路径.zip`。

---

## 🎬 二、 多媒体与视听接口

### 1. 获取图片缩略图
- **URL**: `/api/thumb` (GET)，Params: `path` (String, 必填)。
- **Response**: 返回 `image/jpeg` 的缩略图数据流（自动生成缓存）。

### 2. 获取时光相册列表
- **URL**: `/api/photos` (GET)。返回按时间倒序排列的图片 JSON 数组。

### 3. 获取影视海报墙数据
- **URL**: `/api/media/wall` (GET)。底层利用 `MediaScraper` 刮削第三方开源 API，返回含 `title`, `poster`, `rating`, `overview` 的 JSON 数组。

---

## 🔗 三、 分享接口

### 1. 生成安全分享链接
- **URL**: `/api/share`
- **Method**: `POST`
- **Body**: `path` (必填), `pass` (选填提取码), `expire` (选填有效期毫秒数)。
- **Response**: 返回 16 位随机 Token。

### 2. 访问分享
- **URL**: `/share/{token}` (GET)。
- **Params**: 如果带提取码，需携带 `?pass=1234`。

---

## 🔍 四、 搜索与工具接口

### 1. 全局递归搜索
- **URL**: `/api/search` (GET)，Params: `q` (String, 必填)。返回匹配的 `name`, `path`, `dir`, `size` 等。

### 2. 局域网剪贴板
- **获取**: `GET /api/clipboard` -> 返回纯文本。
- **设置**: `POST /api/clipboard` (Body: `content=文字`)。

### 3. 算数验证码 (v4.5.0 新增)
- **URL**: `/api/captcha` (GET)
- **Response**: `200 OK`。当触发防爆破时调用，返回一道算数题。
```json
{ "q": "3 + 5 = ?", "id": "8" }
```
*(注：前端算出结果后，后续请求需放入 `X-Captcha` Header 中)*

---

## 📡 五、 服务大盘与网络中枢 (v4.5.0 新增)

### 1. 端口探针大盘
- **URL**: `/api/ports`
- **Method**: `GET`
- **Response**: `200 OK`。多线程并发探测本机各项服务的存活状态与 TCP 连接延迟。
```json
[
  { "name": "Web 控制台", "port": 8080, "status": true, "latency": 2 },
  { "name": "WebDAV", "port": 8081, "status": true, "latency": 1 },
  { "name": "Jellyfin", "port": 8096, "status": false, "latency": 0 }
]
```

### 2. 网络测速探针
- **URL**: `/api/speedtest`
- **Method**: `GET`
- **Params**: `size` (选填，默认 20MB)
- **Response**: `200 OK`。生成指定大小的 0 字节流 (`application/octet-stream`)，用于前端计算局域网下行速率。

### 3. 短信中心日志
- **URL**: `/api/sms/logs`
- **Method**: `GET`
- **Response**: `200 OK`。返回最近 50 条收到的短信记录。
```json
[
  { "sender": "1069xxxx", "body": "您的验证码是 123456", "time": "10-26 12:00" }
]
```

### 4. 发送短信
- **URL**: `/api/sms/send`
- **Method**: `POST`
- **Body**: `phone=接收号码&content=短信内容`
- **Response**: `200 OK`。服务端会调用底层 SmsManager 发送，并触发 Webhook。

---

## ⚙️ 六、 系统与安全控制台接口

### 1. 硬件状态监控
- **URL**: `/api/monitor` (GET)
- **Response**: 返回 CPU、内存、存储、电量及 **电池温度 (`temp`)**。

### 2. 多用户管理 (仅 Admin)
- `GET /api/users` 获取列表。
- `POST /api/users` 添加新用户 (Body: `u=用户名&p=密码&r=/目录`)。

### 3. 获取/保存系统配置
- **URL**: `/api/settings` (GET / POST)
- **GET Response**: 包含所有基础、协议、Webhook、安全防线的配置项。
- **POST Body (v4.5.0 扩展)**:
  - 基础：`alias`, `webPort`, `davPort`, `ftpPort`, `webdav`, `ftp`, `dlna`, `stream`, `clipboard`, `lowPower`, `globalMode`。
  - 传统安全：`fail2ban`, `pinRequired`, `auditEnabled`, `ipWhitelistEnabled`, `ipWhitelist`。
  - **五重防线新增**：`lanOnlyStrict` (局域网锁), `captchaEnabled` (算数验证码), `ccLimitEnabled` (CC限流), `secretPath` (隐藏暗号), `sessionTimeout` (闲置登出分钟数), `hideHeader` (隐藏指纹)。
  - 敏感验证：如果开启了 `pinRequired`，必须传递 `pin=安全码`。

### 4. 2FA 两步验证 (v4.5.0 新增)
- **获取状态与密钥**: `GET /api/2fa` -> 返回 `{ "enabled": false, "secret": "JBSWY3DPEHPK3PXP" }`
- **开启/关闭**: `POST /api/2fa` (Body: `enable=true/false&secret=xxx`)。

### 5. 修改管理员密码
- **URL**: `/api/password` (POST)，Body: `password=新密码`。

### 6. 执行 Root Shell 命令 (仅 Admin)
- **URL**: `/api/shell` (POST)，Body: `cmd=命令&pin=安全码`。含极危指令拦截。

### 7. Webhook 测试
- **URL**: `/api/webhook/test` (POST)。

### 8. 🚨 终极自毁模式 (Panic Wipe)
- **URL**: `/api/system/wipe` (POST)，Body: `password=管理员密码&root=true/false`。
- **Response**: `200 OK`，返回 `WIPING` 后立刻断开连接。

### 9. 安全审计与设备管理
- `GET /api/security/sessions` (在线设备)
- `GET /api/security/audit` (审计日志)
- `POST /api/security/pin` (修改安全码: `oldPin=旧码&newPin=新码`)

### 10. PWA 静态资源
- `/manifest.json` / `/sw.js` (GET)。

---

## 🖥️ 七、 远程控制 WebSocket 协议 (8082端口)

- **URL**: `ws://<ip>:8082/?token=<Base64(账号:密码)>`
  - **注意**：仅限 Admin 角色，非法连接直接 Close Code `1008` 断开。
- **控制指令**：`tap,x,y` (点击), `swipe,x1,y1,x2,y2` (滑动), `start_screenshot` (切截图流), `stop` (断开)。

---

## 📡 八、 跨端协同 UDP 协议 (8090端口)

1. 广播 `DISCOVER_TV`。
2. 电视回复 `TV_HERE`。
3. 配置下发：`POST http://<TV_IP>:8090/sync` (ip=xxx&user=xxx&pass=xxx)。
4. 投屏下发：`GET http://<TV_IP>:8090/play?<视频URL>`。

---

## 📂 九、 其他协议端口分配

- **WebDAV (8081)** / **FTP (2121)** / **LocalSend (53317)**。