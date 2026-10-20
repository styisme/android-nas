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
│   │   ├── LocalSendServer.java         # LocalSend 接收端
│   │   ├── RootManager.java             # Root/ADB 底层命令执行器
│   │   └── AdvancedKeepAliveManager.java# 极客级系统保活注入
│   └── src/main/assets/index.html       # MD3 网页前端源码
│
├── client/                      # 手机客户端源码 (Android 5.0+)
├── tv/                          # TV 端源码 (Android 4.4+)
├── watch/                       # 手表端源码 (Android 5.0+)
├── desktop/                     # 桌面端源码 (Python GUI)
├── cli/                         # 命令行客户端源码 (Python CLI)
│
├── .github/workflows/           # 全量发版与独立构建 CI/CD
└── sync_version.py              # 🔥 版本号全自动同步核心脚本
```

---

## 🛠 编译环境隔离 (严禁混用)

- **服务端**：JDK 8 + Gradle 6.7.1 + AGP 4.2.2 (`./gradlew assembleDebug`)
- **客户端 / TV / 手表**：JDK 11 + Gradle 7.5 + AGP 7.4.2 (`cd client && ./gradlew assembleDebug`)
- **桌面端 / CLI**：Python 3.8+ (`python desktop/nas_client.py`)

---

## 📡 服务端 HTTP API 接口规范 (8080端口)

**基础约定**：
- **Base URL**：`http://<ip>:8080`
- **Auth**：需在 Header 中携带 `Authorization: Basic Base64(账号:密码)`。

### 1. 获取目录列表
- **URL**: `/api/list` (GET)
- **Params**: `path` (String, 必填)

### 2. 文件下载与流媒体分片
- **URL**: `/api/download` (GET)
- **Headers**: 可携带 `Range: bytes=0-1024`。

### 3. 客户端流式 PUT 上传
- **URL**: `/*` (任意文件路径)
- **Method**: `PUT`
- **注意**：必须携带准确的 `Content-Length`。

### 4. 系统控制台配置 (新增)
- **URL**: `/api/settings`
- **Method**: `GET` (获取配置) / `POST` (保存配置)
- **POST Body**: `alias`, `webPort`, `davPort`, `ftpPort`, `webdav`, `ftp`, `dlna`, `stream`, `clipboard`, `lowPower`, `globalMode`。

### 5. 修改密码 (新增)
- **URL**: `/api/password` (POST)
- **Body**: `password=新密码`

### 6. Root 命令执行 (新增)
- **URL**: `/api/shell` (POST)
- **Body**: `cmd=要执行的命令`
- **注意**：服务端内置了危险指令（如 `rm -rf /`）拦截。

---

## 📡 跨端协同 UDP 协议 (8090端口)

本项目的手机端与 TV 端/手表端通信依赖于 UDP 广播协议。详见代码 `SyncHelper.java`。

---

## 🚀 核心发版机制与 CI 规范

**严禁手动修改各端 `build.gradle` 中的版本号。**

发版三步曲：
1. 修改根目录 `CHANGELOG.md` 顶部，增加类似 `## [3.3.0]` 的标题。
2. Push 到 `main` 分支。
3. 触发 **Build All** 流水线，自动输出全平台打包好的 ZIP 文件。

---

## 📝 提交规范 (Conventional Commits)

- `feat`: 新功能
- `fix`: 修复 Bug
- `docs`: 文档变更
- `chore`: 构建/依赖更新
- `refactor`: 代码重构
- `ci`: CI/CD 配置修改

**Scope 必须是以下之一**：`server`, `web`, `client`, `tv`, `watch`, `desktop`, `cli`, `ci`。