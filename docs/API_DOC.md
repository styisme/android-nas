# Android NAS 开发者 API 接口规范

本项目的所有端（Web、手机客户端、TV、Watch、CLI、鸿蒙、苹果、小程序）均通过统一的 HTTP JSON API 与局域网内的服务端通信。此外，为了支持多模态远程控制，另设有一套独立的 WebSocket 实时协议。
以下是完整的接口规范说明（基于 v4.4.0）。

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
  - **防爆破 (Fail2Ban)**：连续 5 次鉴权失败，IP 将被封禁 15 分钟，期间所有请求返回 `429 Too Many Requests`。
  - **IP 白名单**：若服务端开启 IP 白名单且当前 IP 不在白名单内，将直接返回 `403 Forbidden`。
- **多用户隔离**：普通用户的 `path` 参数会被底层自动映射并锁定在其专属沙盒目录内。
- **响应格式**：除流媒体下载、纯文本预览和 WebSocket 外，所有响应均为 JSON 格式。

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
    {
      "name": "Inception.mp4",
      "dir": false,
      "size": 1024000
    }
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

### 4. 客户端流式 PUT 上传 (手机端/CLI端使用)
- **URL**: `/*` (任意文件路径，如 `/Movies/new.mp4`)
- **Method**: `PUT`
- **Body**: 纯二进制流 (`application/octet-stream`)。
- **注意**：客户端必须携带准确的 `Content-Length`。如果服务端写入异常，会触发 Drain 机制并返回明确状态码。

### 5. 大文件秒传与分片上传 (v4.4.0 实装)
- **秒传校验**:
  - `POST /api/upload/check?hash=文件MD5`
  - 返回: `SECONDS_UPLOAD`（秒传成功，服务端已有该文件）或 `NEED_UPLOAD`（需要继续上传）。
- **分片上传**:
  - `POST /api/upload/chunk?hash=文件MD5&index=当前分片序号&total=总分片数&name=文件名&path=目标目录`
  - Body: 纯二进制分片数据（通常为 2MB 块）。
  - 服务端底层使用 `FileOutputStream(..., true)` 将分片追加至 `文件名.hash.part` 临时文件。当 `index == total - 1` 时，自动合并重命名为正式文件，并触发 Webhook 通知。

### 6. 多选流式打包下载
- **URL**: `/api/download/zip`
- **Method**: `POST`
- **Body**: `multipart/form-data` 或 `x-www-form-urlencoded`。
  - 需包含 `paths` 字段，多个路径用 `|` 分隔（如 `/a.txt|/b.jpg`）。
- **Response**: `200 OK`，服务端实时压缩并返回 `application/zip` 数据流。

### 7. 获取文本预览
- **URL**: `/api/preview`
- **Method**: `GET`
- **Params**: `path` (String, 必填)
- **Response**: `200 OK`，返回纯文本内容。限制 2MB 以内。

### 8. 新建文件夹 / 重命名 / 递归删除 / 在线保存
- **新建文件夹**: `POST /api/mkdir`，Body: `path=/当前目录&name=新目录名`
- **重命名**: `POST /api/rename`，Body: `path=/旧路径&newName=新名字`
- **递归删除**: `POST /api/delete`，Body: `path=/目标路径`
- **在线保存文本**: `POST /api/save`，Body: `path=/文件路径&content=新的内容` (v4.4.0 起会自动生成带时间戳的 `.bak` 备份)

### 9. 在线解压 ZIP
- **URL**: `/api/unzip`
- **Method**: `POST`
- **Body**: `path=/目标zip文件路径.zip`
- **Response**: `200 OK` (内置了路径穿越校验，解压安全)

---

## 🎬 二、 多媒体与视听接口

### 1. 获取图片缩略图
- **URL**: `/api/thumb`
- **Method**: `GET`
- **Params**: `path` (String, 必填) - 图片的原始路径。
- **Response**: `200 OK`，直接返回 `image/jpeg` 的缩略图数据流（最大 300px）。如果缓存不存在，服务端会自动生成并缓存。

### 2. 获取时光相册列表
- **URL**: `/api/photos`
- **Method**: `GET`
- **Params**: 无。
- **Response**: `200 OK`，返回当前用户根目录下所有图片的 JSON 数组，已按时间倒序排列。
```json
[
  { "name": "IMG_001.jpg", "path": "/Photos/IMG_001.jpg" }
]
```

### 3. 获取影视海报墙数据
- **URL**: `/api/media/wall`
- **Method**: `GET`
- **Params**: 无。
- **Response**: `200 OK`。服务端会扫描全盘视频，通过 `MediaScraper` 引擎调用第三方开源 API 获取元数据。
```json
[
  {
    "name": "Inception.2010.mp4",
    "path": "/Movies/Inception.2010.mp4",
    "title": "盗梦空间",
    "poster": "https://image.tmdb.org/t/p/w300/xxx.jpg",
    "rating": 8.8,
    "overview": "影片简介..."
  }
]
```

---

## 🔗 三、 分享接口

### 1. 生成安全分享链接
- **URL**: `/api/share`
- **Method**: `POST`
- **Body**:
  - `path`: 必填，文件路径。
  - `pass`: 选填，提取码（如 `1234`）。
  - `expire`: 选填，有效期毫秒数（如 `86400000` 表示 1 天）。
- **Response**: `200 OK`，返回 16 位随机 Token (如 `abcd1234efgh5678`)。

### 2. 访问分享
- **URL**: `/share/{token}`
- **Method**: `GET`
- **Params**: 如果有提取码，需携带 `?pass=1234`。
- **Response**: 校验通过后，返回文件流。（注：v4.4.0 起分享链接在底层强制绑定了生成者的 Owner 身份进行沙箱解析）

---

## 🔍 四、 搜索与工具接口

### 1. 全局递归搜索
- **URL**: `/api/search`
- **Method**: `GET`
- **Params**: `q` (String, 必填) - 搜索关键字。
- **Response**: `200 OK`，返回包含 `name`, `path`, `dir`, `size` 等属性的 JSON 数组。

### 2. 局域网剪贴板
- **获取**: `GET /api/clipboard` -> 返回纯文本内容。
- **设置**: `POST /api/clipboard`，Body: `content=要同步的文字`。

---

## ⚙️ 五、 系统与安全控制台接口

### 1. 硬件状态监控
- **URL**: `/api/monitor`
- **Method**: `GET`
- **Response**: `200 OK`
```json
{
  "cpu": 12.5,
  "memUsed": 256,
  "memTotal": 4096,
  "storageUsed": 64,
  "storageTotal": 128,
  "battery": 85
}
```

### 2. 多用户管理 (仅 Admin)
- **获取用户列表**: `GET /api/users` -> 返回用户信息 JSON 数组。
- **添加新用户**: `POST /api/users`，Body: `u=用户名&p=密码&r=/Users/目录名`

### 3. 系统 OTA 更新检查
- **URL**: `/api/ota`
- **Method**: `GET`
- **Response**: `200 OK`，返回最新版本信息，供客户端比对。

### 4. 获取/保存系统配置
- **URL**: `/api/settings`
- **Method**: `GET` / `POST`
- **GET Response**: 返回包含基础配置 (`alias`, `webPort`, `davPort` 等)、协议开关 (`webdav`, `ftp`, `dlna` 等)、极客配置 (`lowPower`, `globalMode`)、Webhook 配置 (`webhookUrl`, `webhookChatId`) 的 JSON。
- **POST Body (v4.4.0 扩展)**:
  - 安全类：`fail2ban` (防爆破开关), `pinRequired` (敏感操作需安全码), `auditEnabled` (审计日志), `ipWhitelistEnabled` (IP白名单开关), `ipWhitelist` (如 `192.168.1.*`)。
  - 敏感验证：如果开启了 `pinRequired`，必须额外传递 `pin=当前安全码`，否则返回 `403 Forbidden`。

### 5. 修改管理员密码
- **URL**: `/api/password`
- **Method**: `POST`
- **Body**: `password=新密码`
- **注意**：底层使用 SHA-256 加盐哈希存储，修改后无法找回。

### 6. 执行 Root Shell 命令 (仅 Admin)
- **URL**: `/api/shell`
- **Method**: `POST`
- **Body**: `cmd=要执行的命令&pin=安全码`
- **安全机制**：服务端会拦截包含 `rm -rf /`、`mkfs`、`dd if=` 等极危指令并返回 `403 Forbidden`。

### 7. Webhook 测试
- **URL**: `/api/webhook/test`
- **Method**: `POST`
- **Response**: `200 OK`，服务端会立即向已配置的 Webhook URL 发送一条测试通知。

### 8. 🚨 终极自毁模式 (Panic Wipe)
- **URL**: `/api/system/wipe`
- **Method**: `POST`
- **Body**:
  - `password`: 必填，管理员密码。
  - `root`: `true` 或 `false`，是否连同系统底层一起摧毁（需 Root）。
- **Response**: `200 OK` (返回 `WIPING`)。**注意**：服务端会立刻返回成功并后台启动自毁线程，前端应直接断开连接。

### 9. 安全审计与设备管理 (仅 Admin)
- **获取在线设备**: `GET /api/security/sessions` -> 返回当前活跃的 IP 与最后访问时间。
- **获取审计日志**: `GET /api/security/audit` -> 返回持久化的安全操作记录纯文本。
- **修改紧急安全码**: `POST /api/security/pin`，Body: `oldPin=旧码&newPin=新码`。

### 10. PWA 静态资源
- **URL**: `/manifest.json` / `/sw.js`
- **Method**: `GET`
- **Response**: 直接返回 PWA 的配置文件与 Service Worker 脚本。

---

## 🖥️ 六、 远程控制 WebSocket 协议 (8082端口)

为了实现极低延迟的屏幕推流与触控，本项目在 8082 端口独立运行了一套 WebSocket 服务。

- **URL (v4.4.0 鉴权升级)**: `ws://<ip>:8082/?token=<Base64(账号:密码)>`
  - **注意**：不支持匿名连接，且仅限 `admin` 角色。服务端会严格校验 token 并将非法连接断开（Close Code `1008`）。
- **数据类型**：支持 Binary（视频/图像流）与 Text（控制指令）。

### 1. 服务端下发 (Server -> Client)
- **H264 视频流**：当用户在服务端授权录屏后，下发 H264 裸流（Binary 类型）。
- **JPEG 截图流**：若前端触发了截图模式，下发 JPEG 图像数据。

### 2. 客户端上报指令 (Client -> Server)
前端发送纯文本字符串：
- **模拟点击**：`tap,x,y` （如 `tap,500,800`）
- **模拟滑动**：`swipe,x1,y1,x2,y2` （如 `swipe,500,800,500,200`）
- **启动截图模式**：`start_screenshot`
- **停止推流/控制**：`stop`

---

## 📡 七、 跨端协同 UDP 协议 (8090端口)

1. **发送方 (手机/手表)**：向局域网 `255.255.255.255:8090` 发送字符串：`DISCOVER_TV`。
2. **接收方 (TV/手表)**：接收到该广播后，向发送方源 IP 单播回复：`TV_HERE`。
3. **配置下发**：手机向 `http://<TV_IP>:8090/sync` 发送 HTTP POST，Body 为 `ip=xxx&user=xxx&pass=xxx`。
4. **投屏下发**：手机向 `http://<TV_IP>:8090/play?<视频绝对URL>` 发送 GET 请求。

---

## 📂 八、 其他协议端口分配

- **WebDAV (8081端口)**：支持标准 WebDAV 协议。在开启 Root 全盘模式下，可直接访问系统底层目录。
- **FTP (2121端口)**：支持 PASV 被动模式，兼容 Windows 资源管理器。
- **LocalSend (53317端口)**：原生实现接收协议，接收的文件自动落盘至 `/Download/LocalSend/` 目录。