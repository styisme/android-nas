# Android NAS 开发者贡献指南与全栈架构规范

感谢参与 Android NAS 的开发！本项目采用了目前开源界极其罕见的 **十端分离架构** 与 **全自动平铺直传 CI/CD 流程**。在提交任何代码之前，请仔细阅读以下核心规范、架构说明以及开发注意事项。

## ⚠️ 关于 AI 生成代码的严正声明

本项目 99% 以上的代码（包括 Java, Python, HTML, Swift, ArkTS, C#, YAML 等）由大型语言模型（LLM）直接输出，人类开发者主要担任“产品经理”与“测试工程师”。提交 PR 时请务必遵循以下原则：
1. **必须经过真机测试**：AI 极易写出大括号不匹配、Lambda 表达式受检异常未捕获、或跨线程操作 UI 的代码。请务必确保代码在你的本地或云端编译通过，且功能在物理设备上验证正常。
2. **声明使用的模型**：在 PR 描述中注明哪些部分由 AI 辅助生成（如：`feat(server): 新增 H264 远控接口 (由 Claude-3.5 辅助生成)`）。
3. **禁止破坏原有极简架构**：如非必要，**严禁引入庞大的第三方依赖**。例如：
   - 不要将服务端的 NanoHTTPD 替换为 Spring Boot。
   - 不要将手机端的 OkHttp 替换为 Retrofit 或引入 RxJava。
   - 不要将桌面端的 Tkinter 替换为 PyQt 或 Electron（为了保持极低的内存占用与秒开速度）。

---

## 📂 十端深度项目结构解析 (v4.2.0 架构)

```text
android-nas/
├── app/                         # 📱 服务端源码 (Android 2.2+)
│   ├── src/main/java/com/androidnas/
│   │   ├── MainActivity.java            # 本地控制中心 (驾驶舱，含 Tab 分页)
│   │   ├── HttpServerService.java       # 核心服务容器 (统一调度所有端口)
│   │   ├── NasHttpServer.java           # 核心 HTTP 引擎 (NanoHTTPD 路由与权限拦截)
│   │   ├── WebDavServer.java            # 纯 Socket 实现的 WebDAV 协议栈
│   │   ├── FtpServer.java               # 纯 Socket 实现的 FTP 协议栈
│   │   ├── DlnaServer.java              # DLNA 投屏发现 (SSDP)
│   │   ├── LocalSendServer.java         # LocalSend 接收端
│   │   ├── DatabaseManager.java         # SQLite 多用户与秒传记录引擎
│   │   ├── MediaScraper.java            # 🆕 免 Key 影视刮削引擎 (纯 Java 正则)
│   │   ├── WebhookManager.java          # 🆕 Webhook 事件推送引擎
│   │   ├── ThumbnailManager.java        # 图片缩略图按需生成引擎
│   │   ├── SystemMonitor.java           # CPU/内存/电池监控读取器
│   │   ├── RootManager.java             # Root/ADB 底层命令执行器
│   │   ├── AdvancedKeepAliveManager.java# 极客级系统保活注入 (OOM 免死)
│   │   ├── RemoteDesktopManager.java    # H264 推流与 WebSocket 远控中枢
│   │   └── NasAccessibilityService.java # 无障碍触控注入双引擎
│   └── src/main/assets/                 # MD3 网页前端与 PWA 文件
│       ├── index.html                   # 网页前端源码 (集成 Video.js/Chart.js)
│       ├── manifest.json                # 🆕 PWA 配置
│       └── sw.js                        # 🆕 Service Worker 缓存
│
├── client/                      # 🖥️ 手机客户端 (Java + MD3, Android 5.0+)
├── tv/                          # 📺 TV 端 (Leanback 卡片流, Android 4.4+)
├── watch/                       # ⌚ 手表端 (RecyclerView, Android 5.0+)
├── desktop/                     # 💻 桌面端 GUI (Python + Tkinter)
├── cli/                         # ⌨️ 命令行工具 (Python + cmd 模块)
├── apple/                       # 🍎 苹果端 (SwiftUI, iOS 15+ / macOS 12+)
├── harmony/                     # 🟦 鸿蒙端 (ArkTS, OpenHarmony 4.0+)
├── miniprogram/                 # 📱 微信小程序端 (WXML/WXSS/JS)
├── browser-extension/           # 🌐 浏览器右键下载扩展 (Manifest V3)
│
├── .github/workflows/           # 自动化 CI/CD 矩阵 (全量发版与独立构建)
└── sync_version.py              # 🔥 版本号全自动同步核心脚本
```

---

## 🛠 编译环境隔离与依赖 (严禁混用)

不同端的工具链天差地别，请务必使用对应的环境进行编译：

| 端类型 | 核心工具链 | 本地编译命令 |
|---|---|---|
| **服务端** | JDK 8 + Gradle 6.7.1 + AGP 4.2.2 | `./gradlew assembleDebug` |
| **安卓客户端** | JDK 11 + Gradle 7.5 + AGP 7.4.2 | `cd client && ./gradlew assembleDebug` |
| **桌面端 / CLI** | Python 3.8+ (依赖 `requests`, `pyinstaller`) | `python desktop/nas_client.py` |
| **苹果端** | macOS 13+ / Xcode 15+ / XcodeGen | `cd apple && xcodegen generate` |
| **鸿蒙端** | DevEco Studio 4.0+ / OpenHarmony SDK | 需在 DevEco 中打开工程 |

---

## 📡 核心通信协议与底层规范

项目高度依赖 HTTP 与 WebSocket，所有端的网络层必须严格遵守以下规范（完整细节请见 `docs/API_DOC.md`）。

### 1. 统一鉴权与多用户沙箱隔离
除 WebSocket 与分享链接外，所有 HTTP 请求必须携带 Header：
`Authorization: Basic Base64(账号:密码)`。
- **底层拦截**：`NasHttpServer.safeFile()` 会从 SQLite 中读取当前用户的 `root_dir`。
- **普通用户**：路径被强制映射并锁定在其专属目录下（如 `/sdcard/Users/user1`）。
- **全盘 Root**：仅 `admin` 账号在开启全盘模式后，可直接越权访问底层目录。

### 2. 文件流式上传与大文件断点续传 (极重要)
- **小文件 PUT 上传**：必须携带准确的 `Content-Length`。禁止使用 `Transfer-Encoding: chunked`，否则会导致 NanoHTTPD 底层无限阻塞卡死。
- **大文件分片上传**：前端通过 `File.slice()` 按 2MB 切块，携带 `hash`、`index`、`total` 请求 `/api/upload/chunk`。服务端利用 `FileOutputStream(..., true)` 实现流式追加合并，并利用 `synchronized` 解决并发冲突。

### 3. 缩略图、影视刮削与打包下载
- **缩略图**：前端请求 `/api/thumb?path=xxx`，服务端在 `.thumbnails` 目录生成 300px 缓存并返回。
- **影视刮削**：前端请求 `/api/media/wall`，服务端 `MediaScraper` 纯正则解析文件名，请求第三方 API 获取海报，绝不使用重型 JSON 库或视频解析库。
- **流式打包下载**：前端 POST 包含 `paths` 数组到 `/api/download/zip`，服务端利用 `ZipOutputStream` 实时压缩并返回，避免内存溢出。

### 4. WebSocket 远控协议 (8082端口)
- **H264 推流**：服务端下发二进制 H264 裸流，前端必须使用 JMuxer 实时封装为 fMP4 并在 `<video>` 中播放。
- **触控指令**：前端发送纯文本字符串，如 `tap,x,y` 或 `swipe,x1,y1,x2,y2`。服务端会自动路由至无障碍服务或 Root input 进行点击注入。

### 5. 跨端协同 UDP 协议 (8090端口)
手机端与 TV 端通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 进行广播发现，实现配置下发与一键投屏。详见 `SyncHelper.java`。

---

## 🚀 核心发版机制与 CI/CD 规范

**🚨 严禁任何人（包括 AI）手动修改各端 `build.gradle` 或 `pubspec.yaml` 中的版本号。**

本项目的发版过程是全自动化的，请严格遵守以下标准作业程序（SOP）：

1. **修改更新日志**：在根目录 `CHANGELOG.md` 顶部，增加新版本号（例如 `## [4.2.0]`）及详细的更新内容。
2. **推送代码**：Push 到 `main` 或 `master` 分支。
3. **自动矩阵编译**：GitHub Actions 会检测到 `CHANGELOG.md` 的变更，触发 `build-all.yml` 矩阵。并行启动 Ubuntu、Windows、macOS 以及 OpenHarmony Docker 环境，全量编译 10 个端。
4. **区分 Debug / Release**：独立日常构建触发 `assembleDebug`，包名带 `.debug` 后缀；`build-all` 触发 `assembleRelease`，使用 GitHub Secrets 注入正式 keystore 进行签名。
5. **自动打 Tag**：CI 会解析 CHANGELOG 中的版本号，自动在 Git 中打上 `v4.2.0` 的标签。
6. **平铺直传发布**：摒弃传统的单一 ZIP 打包方式。CI 脚本会使用 `find -type f` 提取所有产物并重命名，直接平铺展示在 GitHub Release 页面，供用户按需下载。

*(日常修改代码如果未动 `CHANGELOG.md`，只会触发对应端的快速独立编译，不会发版)*

---

## 📝 提交规范 (Conventional Commits)

采用 `<type>(<scope>): <subject>` 规范：

- `feat`: 新功能
- `fix`: 修复 Bug
- `docs`: 文档变更
- `chore`: 构建/依赖更新
- `refactor`: 代码重构
- `ci`: CI/CD 配置修改

**Scope 必须是以下之一**：
`server` (服务端), `web` (网页端), `client` (安卓端), `tv`, `watch`, `desktop` (桌面端), `cli`, `apple` (苹果端), `harmony` (鸿蒙端), `mini` (小程序), `ci`。

**示例**：`fix(server): 修复 SystemMonitor 读取电池信息在 API 8 导致 NoSuchFieldError 闪退`