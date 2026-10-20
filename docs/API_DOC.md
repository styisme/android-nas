# Android NAS 开发者 API 接口规范

本项目的所有端（Web、手机客户端、TV、Watch、CLI）均通过统一的 HTTP JSON API 与局域网内的服务端通信。
以下是完整的接口规范说明。

---

## 📡 基础约定

- **Base URL**：`http://<ip>:8080`
- **Content-Type**：
  - 常规请求：`application/x-www-form-urlencoded` 或 `application/json`
  - 文件上传：`multipart/form-data` 或 `application/octet-stream`
- **鉴权方式 (Auth)**：
  - 若服务端在驾驶舱开启了“账号验证”，需要在 HTTP Header 中携带 Basic Auth。
  - Header 格式：`Authorization: Basic Base64(账号:密码)` （默认 `admin:admin`）。
  - 鉴权失败将返回 `401 Unauthorized`。
- **响应格式**：除流媒体下载和纯文本预览外，所有响应均为 JSON 格式。

---

## 📁 一、 文件管理接口

### 1. 获取目录列表
- **URL**: `/api/list`
- **Method**: `GET`
- **Params**:
  - `path` (String, 必填) - 目标目录的绝对路径，如 `/` 或 `/Movies/`。
- **Response**: `200 OK`
```json
{
  "path": "/Movies/",
  "files": [
    {
      "name": "Inception.mp4",
      "dir": false,
      "size": 1024000,
      "isTxt": false,
      "isMedia": true,
      "isZip": false
    }
  ]
}
```

### 2. 文件下载与流媒体分片
- **URL**: `/api/download`
- **Method**: `GET`
- **Params**:
  - `path` (String, 必填) - 文件的绝对路径。
- **Headers**:
  - 可携带 `Range: bytes=0-1024` 用于视频进度条拖动。
- **Response**:
  - 若携带 Range，返回 `206 Partial Content`，并带有 `Content-Range` 头。
  - 若未携带，返回 `200 OK`，并带有 `Content-Disposition` 触发下载。

### 3. 表单多文件上传 (Web端使用)
- **URL**: `/api/upload`
- **Method**: `POST`
- **Body**: `multipart/form-data` 格式。
  - 需包含 `path` 字段（目标目录）。
  - 一个或多个 `file` 字段。
- **Response**: `200 OK`，返回字符串 `OK: filename.ext`。

### 4. 客户端流式 PUT 上传 (手机端/CLI端使用)
- **URL**: `/*` (任意文件路径，如 `/Movies/new.mp4`)
- **Method**: `PUT`
- **Body**: 纯二进制流 (`application/octet-stream`)。
- **注意**：客户端必须携带准确的 `Content-Length`。如果服务端写入异常（如无权限），会触发 Drain 机制并返回明确状态码。

### 5. 获取文本预览
- **URL**: `/api/preview`
- **Method**: `GET`
- **Params**: `path` (String, 必填)
- **Response**: `200 OK`，返回纯文本内容。限制 2MB 以内，超出返回 400。

### 6. 新建文件夹
- **URL**: `/api/mkdir`
- **Method**: `POST`
- **Body**: `path=/当前目录&name=新目录名`
- **Response**: `200 OK`

### 7. 重命名
- **URL**: `/api/rename`
- **Method**: `POST`
- **Body**: `path=/旧路径&newName=新名字`
- **Response**: `200 OK`

### 8. 递归删除
- **URL**: `/api/delete`
- **Method**: `POST`
- **Body**: `path=/目标路径` (服务端会自动递归删除目录下的所有内容)
- **Response**: `200 OK`

### 9. 在线保存文本
- **URL**: `/api/save`
- **Method**: `POST`
- **Body**: `path=/文件路径&content=新的文件内容`
- **Response**: `200 OK`

### 10. 在线解压 ZIP
- **URL**: `/api/unzip`
- **Method**: `POST`
- **Body**: `path=/目标zip文件路径.zip`
- **Response**: `200 OK` (内置了路径穿越校验，解压安全)

### 11. 生成免密分享链接
- **URL**: `/api/share`
- **Method**: `POST`
- **Body**: `path=/文件路径`
- **Response**: `200 OK`，返回 8 位随机 Token (如 `abcd1234`)。
- **访问分享**：外部用户通过 `GET /share/{token}` 即可免密直接下载该文件。

---

## 🔍 二、 搜索与工具接口

### 1. 全局递归搜索
- **URL**: `/api/search`
- **Method**: `GET`
- **Params**: `q` (String, 必填) - 搜索关键字。
- **Response**: `200 OK`，返回包含 `name`, `path`, `dir`, `size` 等属性的 JSON 数组。

### 2. 局域网剪贴板
- **获取剪贴板**: `GET /api/clipboard` -> 返回纯文本内容。
- **设置剪贴板**: `POST /api/clipboard`，Body: `content=要同步的文字`。

---

## ⚙️ 三、 系统与控制台接口

### 1. 获取服务端状态
- **URL**: `/api/status`
- **Method**: `GET`
- **Response**:
```json
{
  "lowPower": false,
  "clipboardEnabled": true,
  "globalMode": false
}
```

### 2. 获取/保存系统配置
- **URL**: `/api/settings`
- **Method**: `GET` / `POST`
- **GET Response**: 返回包含 `alias`, `webPort`, `davPort`, `ftpPort`, `webdav`, `ftp`, `dlna`, `stream`, `clipboard`, `lowPower`, `globalMode` 的 JSON。
- **POST Body**: 提交上述字段的键值对即可覆盖。

### 3. 修改管理员密码
- **URL**: `/api/password`
- **Method**: `POST`
- **Body**: `password=新密码`
- **Response**: `200 OK`

### 4. 执行 Root Shell 命令
- **URL**: `/api/shell`
- **Method**: `POST`
- **Body**: `cmd=要执行的命令`
- **Response**: `200 OK`，返回命令的标准输出。
- **安全机制**：服务端会拦截包含 `rm -rf /`、`mkfs`、`dd if=` 等极危指令并返回 `403 Forbidden`。

---

## 📡 四、 跨端协同 UDP 协议 (8090端口)

本项目的手机端与 TV 端/手表端通信依赖于 UDP 广播协议。

1. **发送方 (手机/手表)**：向局域网 `255.255.255.255:8090` 发送字符串：`DISCOVER_TV`。
2. **接收方 (TV/手表)**：接收到该广播后，向发送方源 IP 单播回复：`TV_HERE`。
3. **配置下发**：手机收到 IP 后，向 `http://<TV_IP>:8090/sync` 发送 HTTP POST，Body 格式为 `ip=xxx&user=xxx&pass=xxx`。
4. **投屏下发**：手机向 `http://<TV_IP>:8090/play?<视频绝对URL>` 发送 GET 请求，TV 端立刻拉起播放器。

---

## 📂 五、 其他协议端口分配

- **WebDAV (8081端口)**：支持标准 WebDAV 协议，支持 `PROPFIND`, `MKCOL`, `PUT`, `DELETE` 等指令。在开启 Root 全盘模式下，可直接访问系统底层目录。
- **FTP (2121端口)**：支持 PASV 被动模式，兼容 Windows 资源管理器，支持 `STOR`, `RETR`, `DELE`, `MKD`, `RMD`, `RNFR`, `RNTO` 等全套指令。
- **LocalSend (53317端口)**：原生实现 LocalSend 接收协议，接收的文件自动落盘至 `/Download/LocalSend/` 目录。