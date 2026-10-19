# Android NAS 开发者贡献指南与 API 规范

感谢参与 Android NAS 的开发！本项目采用了多端分离的架构与全自动 CI/CD 流程。在提交任何代码之前，请仔细阅读以下核心规范、架构说明以及详尽的 API 接口文档。

## ⚠️ 关于 AI 生成代码的声明

本项目 90% 以上的代码由大型语言模型（LLM）生成。提交 PR 时请遵循以下原则：
1. **必须在真机测试**：AI 极易写出大括号不匹配或受检异常（如 `IOException`）未捕获的代码，请务必确保编译通过且功能正常。
2. **声明模型**：在 PR 描述中注明哪些部分由 AI 辅助生成（如：`feat(server): 新增 xx 接口 (由 GPT-4 辅助生成)`）。
3. **禁止破坏原有极简架构**：如非必要，禁止引入庞大的第三方依赖（如将 `OkHttp` 替换为 `Retrofit`，或引入 `RxJava`）。

---

## 📂 深度项目结构解析

```text
android-nas/
├── app/                         # 服务端源码 (Android 2.2+)
│   ├── src/main/java/com/androidnas/
│   │   ├── MainActivity.java            # NAS 本地控制中心 (驾驶舱)
│   │   ├── HttpServerService.java       # 核心服务容器 (统一调度所有端口)
│   │   ├── NasHttpServer.java           # 核心 HTTP 引擎 (NanoHTTPD 路由)
│   │   ├── WebDavServer.java            # 纯 Socket 实现的 WebDAV 栈
│   │   ├── FtpServer.java               # 纯 Socket 实现的 FTP 栈
│   │   ├── DlnaServer.java              # DLNA 投屏发现
│   │   └── LocalSendServer.java         # LocalSend 接收端
│   └── src/main/assets/index.html       # MD3 网页前端源码
│
├── client/                      # 手机客户端源码 (Android 5.0+)
│   ├── src/main/java/com/androidnas/client/
│   │   ├── MainActivity.java            # 多设备管理、局域网扫描
│   │   ├── FileListActivity.java        # 多协议智能路由 (原生/WebDAV/FTP)
│   │   ├── SyncHelper.java              # UDP 协同发报器
│   │   └── SettingsActivity.java        # 设置中心
│
├── tv/                          # TV 端源码 (Android 4.4+)
├── watch/                       # 手表端源码 (Android 5.0+)
├── desktop/                     # 桌面端源码 (Python)
│
├── .github/workflows/           # 五端独立 CI/CD
└── sync_version.py              # 🔥 版本号全自动同步核心脚本
```

---

## 🛠 编译环境隔离 (严禁混用)

- **服务端**：JDK 8 + Gradle 6.7.1 + AGP 4.2.2 (`./gradlew assembleDebug`)
- **客户端 / TV / 手表**：JDK 11 + Gradle 7.5 + AGP 7.4.2 (`cd client && ./gradlew assembleDebug`)
- **桌面端**：Python 3.8+ (`python desktop/nas_client.py`)

---

## 📡 服务端 HTTP API 接口规范 (8080端口)

**基础约定**：
- **Base URL**：`http://<ip>:8080`
- **Auth**：需在 Header 中携带 `Authorization: Basic Base64(账号:密码)`。若服务端控制中心关闭了账号验证，则无需携带。
- **Content-Type**：除文件上传/流式传输外，均为 `application/x-www-form-urlencoded` 或 `application/json`。

### 1. 获取目录列表
- **URL**: `/api/list`
- **Method**: `GET`
- **Params**: `path` (String, 必填，例如 `/Movies/`)
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
    },
    {
      "name": "Subtitle",
      "dir": true,
      "size": 0,
      "isTxt": false,
      "isMedia": false,
      "isZip": false
    }
  ]
}
```

### 2. 文件下载与流媒体分片
- **URL**: `/api/download`
- **Method**: `GET`
- **Params**: `path` (String, 必填)
- **Headers**: 可携带 `Range: bytes=0-1024` 用于视频进度条拖动。
- **Response**:
  - 若携带 Range 请求头，返回 `206 Partial Content`，并带有 `Content-Range` 头。
  - 否则返回 `200 OK`，`Content-Type` 自动识别（如 `video/mp4`, `application/octet-stream`）。

### 3. 多文件表单上传
- **URL**: `/api/upload`
- **Method**: `POST`
- **Body**: `multipart/form-data` 格式。需包含 `path` 字段（目标目录）与一个或多个 `file` 字段。
- **Response**: `200 OK` 返回字符串 `OK: filename.ext`。

### 4. 客户端流式 PUT 上传
- **URL**: `/*` (任意文件路径，如 `/Movies/new.mp4`)
- **Method**: `PUT`
- **Body**: 纯二进制流 (`application/octet-stream`)。
- **注意**：OkHttp 或 WebDAV 客户端必须携带准确的 `Content-Length`，否则 NanoHTTPD 会无限阻塞等待。

### 5. 预览文本
- **URL**: `/api/preview`
- **Method**: `GET`
- **Params**: `path` (String, 必填)
- **Response**: 返回文件纯文本内容（限制 2MB 以内，超出返回 400）。

### 6. 新建文件夹 / 重命名 / 删除
- **新建**: `POST /api/mkdir`，Body: `path=/当前目录&name=新目录名`
- **重命名**: `POST /api/rename`，Body: `path=/旧路径&newName=新名字`
- **删除**: `POST /api/delete`，Body: `path=/目标路径`（服务端会自动递归删除）。

### 7. 在线解压 ZIP
- **URL**: `/api/unzip`
- **Method**: `POST`
- **Body**: `path=/目标zip文件路径.zip`
- **Response**: `200 OK` 表示解压完成。服务端内置路径穿越校验，解压安全。

### 8. 剪贴板同步
- **获取**: `GET /api/clipboard` -> 返回纯文本内容。
- **设置**: `POST /api/clipboard`，Body: `content=要同步的文字`。

### 9. 生成免密分享
- **URL**: `/api/share`
- **Method**: `POST`
- **Body**: `path=/文件路径`
- **Response**: 返回 8 位随机 Token（如 `abcd1234`）。外部用户访问 `/share/abcd1234` 即可免密直接下载该文件。

---

## 📡 跨端协同 UDP 协议 (8090端口)

本项目的手机端与 TV 端/手表端通信依赖于 UDP 广播协议：
1. **发送方 (手机)**：向局域网 `255.255.255.255:8090` 发送字符串：`DISCOVER_TV`。
2. **接收方 (TV/手表)**：接收到该广播后，向发送方源 IP 单播回复：`TV_HERE`。
3. **配置下发**：手机收到 IP 后，向 `http://<TV_IP>:8090/sync` 发送 HTTP POST，Body 格式为 `ip=xxx&user=xxx&pass=xxx`。
4. **投屏下发**：手机向 `http://<TV_IP>:8090/play?<视频绝对URL>` 发送 GET 请求，TV 端立刻启动播放器。

---

## 🚀 核心发版机制与 CI 规范

**严禁手动修改各端 `build.gradle` 中的版本号。**

发版三步曲：
1. 修改根目录 `CHANGELOG.md` 顶部，增加类似 `## [3.2.0]` 的标题。
2. Push 到 `main` 分支。
3. Actions 会运行 `sync_version.py` 自动同步并打包。

---

## 📝 提交规范 (Conventional Commits)

采用 `<type>(<scope>): <subject>` 规范：
- `feat`: 新功能
- `fix`: 修复 Bug
- `docs`: 文档变更
- `chore`: 构建/依赖更新
- `refactor`: 代码重构

**Scope 必须是以下之一**：`server`, `web`, `client`, `tv`, `watch`, `desktop`, `ci`。
示例：`fix(server): 修复 getResponseCode 未捕获 IO 异常导致崩溃`。