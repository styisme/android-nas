# Android NAS (十端生态矩阵)

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20HarmonyOS%20%7C%20Win%20%7C%20Linux%20%7C%20Web-brightgreen.svg)]()
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![Min SDK TV](https://img.shields.io/badge/TV_Min_SDK-API_19-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> **把吃灰的 Android 手机变成全平台私有云控制中枢。**
> 本项目不仅是一个轻量级的文件服务器，而是一个包含了服务端、网页端、手机客户端、电视盒子端、智能手表端、桌面 GUI、极客 CLI，甚至涵盖了苹果原生、鸿蒙原生与微信小程序的**十端全平台生态闭环**。
> 
> 原生内置 WebDAV、FTP、LocalSend、DLNA 四大主流局域网协议；支持跨端协同投屏与配置秒下发；手机端支持相册自动增量备份；全端支持局域网剪贴板同步；在 Root 特权加持下，更能解锁底层目录（`/data`, `/system`）访问、Web Shell 终端执行、以及基于 H264 硬件编码的多模态云端远控。
>
> **🔥 多核生态驱动 (Multi-Core Architecture)**：本项目除了主打极限兼容的 Java 主干外，还在生态上进行了深度扩展，独立孵化了 **.NET 10 (MAUI)** 次世代分支与纯血 **HarmonyOS (ArkTS)** 分支。详见下方链接。

---

## 🧬 子项目与生态矩阵联动

为了让不同的技术栈发挥极致的性能，部分核心端已独立建仓维护，并与主项目保持 API 100% 互通：

- 👉 **[android-nas-lite](https://github.com/styisme/android-nas-lite)**（精简版）：剥离远控、Root 全盘、Web Shell、内网穿透等高风险功能与低使用率模块，专注 HTTP/WebDAV/FTP 文件服务主线。跟随完整版大版本同步更新，非独立分叉。
- 👉 **[android-nas-dotnet](https://github.com/styisme/android-nas-dotnet)**（高版本现代重构）：基于 .NET 10 (MAUI) 与 ASP.NET Core，彻底拥抱现代 Android 权限模型，提供跨平台原生 GUI 体验。
- 👉 **[HarmonyNAS](https://github.com/styisme/HarmonyNAS)**（纯血鸿蒙原生）：基于纯正 ArkTS 重写，拒绝任何黑科技，利用系统级 `backgroundTaskManager` 合法保活，适配鸿蒙全家桶。

---

## 📚 全局文档矩阵 (Documentation)

本项目拥有极其详尽的文档体系，无论你是小白还是极客，都能找到你需要的说明书：

- 📖 **[终极使用与运维指南 (USAGE.md)](docs/USAGE.md)**：
  从零部署、网络映射（WebDAV/FTP）、跨端协同配置、相册备份实操、远控配置、CLI 终端指令大全以及深度故障排查。这是你上手 NAS 的第一站。
- 🔌 **[开发者 API 接口规范 (API_DOC.md)](docs/API_DOC.md)**：
  包含服务端 8080 端口的所有 HTTP API 细节、WebDAV 底层协议规范、跨端协同 UDP 协议以及特殊端口分配。
- 🏗️ **[底层架构深度解析 (ARCHITECTURE.md)](docs/ARCHITECTURE.md)**：
  深入剖析纯 Socket 手写协议栈、H264 远控推流原理、极限兼容 API 8 的妥协之道、全盘突破机制以及四层保活矩阵。
- 🛡️ **[安全策略与高危警告 (SECURITY.md)](SECURITY.md)**：
  **（高危必读）** 使用全盘模式、Web Shell 和公网穿透前，请务必阅读此文档，了解明文传输风险与防变砖守则。
- 🤝 **[开发者贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md)**：
  AI 生成代码的声明、深度项目结构图、多环境编译隔离说明、代码提交规范。
- 🤖 **[AI 生成声明 (AI_DISCLOSURE.md)](docs/AI_DISCLOSURE.md)**：
  关于本项目 99% 代码由大模型生成的真实开发流程与局限性声明。
- 📝 **[版本更新日志 (CHANGELOG.md)](CHANGELOG.md)**：
  追溯项目从 v1.0.0 到 v4.2.0 的完整演进史。

---

## 🚨 最重要：关于下载与发版机制

由于本项目采用极其激进的 CI/CD 自动化迭代，GitHub 右侧的 Releases 页面往往滞后于最新代码。**请直接前往 GitHub Actions 页面下载最新构建产物！**

👉 **[点此进入 Android NAS 主干 Actions 获取最新版构建产物](https://github.com/styisme/android-nas/actions)**

> **💡 极客一键全量平铺发版模式**：
> 本项目配置了极其严谨的双轨制 CI/CD：
> 1. **日常改代码**：只触发对应端的快速独立编译（已配置忽略 `.md` 等文档修改）。
> 2. **全量发版**：每次修改根目录 `CHANGELOG.md` 并推送，CI 会瞬间启动 `build-all.yml` 矩阵，将 **10 个端** 全部并行编译。最终产物不会被塞进恶心的 ZIP，而是自动重命名并**直接平铺展示在 Releases 页面的 Assets 列表中**，供用户按需下载。

---

## 🌟 为什么选择本项目？（碾压级核心优势）

市面上的手机 NAS 软件往往存在各种妥协：要么不支持老旧系统，要么 UI 落后，或者受限于单一平台。本项目专为极客打造，具备以下优势：

1. **十端合一，全场景覆盖**：服务端、Web、手机、TV、手表、桌面、CLI、苹果、鸿蒙、小程序。无论你在哪块屏幕上，都能无缝访问你的数据。
2. **多协议制霸**：原生实现 WebDAV、FTP、LocalSend、DLNA。在开启 Root 全盘模式后，这些协议可直接穿透 Android 沙盒访问系统底层。
3. **极客特权（Root/ADB）**：支持 OOM 免死注入、静音音频保活、Web Shell 终端。网页端甚至集成了 H264 远控引擎，鼠标点哪手机点哪。
4. **极致兼容与美观并存**：服务端最低兼容十年前的 Android 2.2 (API 8)；手机端和网页端全面采用极致的 Material Design 3 规范，Web 端现已支持 PWA（添加到主屏幕，类原生体验）。
5. **多核架构进化与家庭云蜕变**：保留了对上古设备极致兼容的 Java 主干。同时，v4.2.0 引入了 SQLite 多用户数据库、缩略图引擎、TB 级大文件断点续传与秒传引擎，正式从“单机玩具”蜕变为“家庭私有云”。

---

## ✨ 十端详细功能矩阵 (v4.2.0 全新特性)

### 📱 服务端 (Server - 装在闲置手机)
- **多端口并发调度**：
  - `8080`：Web 网页管理 / JSON API / 流媒体分片。
  - `8081`：标准 WebDAV 协议（**纯 Socket 手写实现**）。
  - `2121`：原生 FTP 服务（支持 PASV 被动模式）。
  - `53317`：LocalSend 接收端。
  - `8090`：跨端协同发现服务（UDP）。
  - `8082`：WebSocket 远控服务（H264 推流与触控指令）。
- **多用户隔离 (SQLite)**：底层生成 `nas_core.db`，支持多账号注册，不同账号自动映射到独立的物理目录，互不干扰。
- **缩略图引擎**：自动解析图片并生成 300px 小图缓存至 `.thumbnails`，实现海量照片秒级加载。
- **硬件监控 API**：实时输出 CPU、内存、存储空间与电池电量（自带 API 8 兼容守卫）。
- **全盘 Root 模式与无敌保活**：一键突破沙盒，静音音频欺骗与 Root 注入免死。
- **大文件秒传与断点续传 (新增)**：底层实装 MD5 秒传校验与 2MB 分片流式合并，支持超大文件无视网络波动的稳定上传。
- **Webhook 自动化通知 (新增)**：支持配置 Telegram/企业微信 Webhook，实现文件上传、异常登录等事件的实时推送。

### 🌐 Web 网页端 (MD3 Web)
- **多模态远程桌面**：H264 硬件编码 30fps 推流 + Root 截图降级方案，支持触控反向控制。
- **PWA 渐进式应用 (新增)**：集成 `manifest.json` 与 `Service Worker`，可通过 Chrome 添加到手机桌面，全屏无浏览器地址栏，体验等同原生 App。
- **时光相册 (新增)**：获取全盘图片 EXIF 时间，按瀑布流倒序展示，极度丝滑。
- **影视海报墙 (升级)**：接入免费开源影视元数据接口（免 Key 纯 Java 解析），自动提取文件名并刮削真实海报、评分与简介，告别枯燥的文件名。
- **硬件仪表盘 (新增)**：实时图表展示服务端的运行状态。
- **用户管理面板 (新增)**：直接在网页端添加新用户、分配存储根目录。
- **流式打包下载 (新增)**：在网页上多选多个文件，服务端实时压缩成 ZIP 流并下载，不占用本地空间。
- **安全分享 (新增)**：生成免密短链时，可设置有效期（如 1 天）和提取码（如 1234）。
- **系统控制台**：在网页端直接修改密码、修改端口、开关协议及配置 Webhook。

### 🖥️ 手机客户端 (Client - Android 5.0+)
- **相册自动增量备份**：利用 `MediaStore` 扫描本地，比对 NAS `/Photos/` 目录，只传新照片。
- **局域网一键扫描**：多线程并发探测。
- **远控与控制台**：原生 `WebViewActivity`，一键全屏打开终端与远控面板。
- **分享外链**：直接调起系统分享面板发送免密链接。

### 💻 桌面端 (Desktop - Win/Linux)
- **纯原生 Tkinter**：无内置浏览器，秒开不卡顿。
- **全局搜索与剪贴板**：顶部全局搜索与剪贴板双向同步。
- **一键复制外链**：选中文件生成免密短链并自动复制到电脑剪贴板。
- **在线编辑与解压**：多选并发上传，自带在线文本编辑与 ZIP 解压。

### ⌨️ CLI 命令行端 (Win/Linux)
- **交互式 Shell**：输入 `shell` 进入，支持 Tab 路径自动补全。
- **全指令支持**：支持 `ls`、`cd`、`get`、`put`、`find`、`unzip`、`share`、`exec`。

### 🍎 苹果端 (SwiftUI) & 🟦 鸿蒙端 (ArkTS)
- **苹果端**：原生编写，全端对齐了搜索、剪贴板同步与控制台唤起。
- **鸿蒙端**：基于 ArkUI 声明式开发，一套代码自适应手机/平板/PC，依托官方长时任务合法驻留。

### 📺 TV & ⌚ 手表端
- **TV 端**：Leanback 卡片流 4 列网格，遥控器焦点移动 1.1 倍放大动画。
- **手表端**：RecyclerView 卡片布局，按压动画。支持媒体预览与下载至专属沙盒。

### 📱 微信小程序端
- **免安装即用**：支持全盘文件搜索、剪贴板浮层、以及生成免密分享短链。

---

## 🔗 跨端协同与黑科技玩法

1. **一键下发配置**：手机端点击“发送配置到电视”，通过 UDP 发现电视并下发 NAS 密码。
2. **手机/手表投屏**：点击视频发送 Play 指令，电视端瞬间拉起播放。
3. **全端剪贴板同步**：手机复制文字，网页端、桌面端直接粘贴。

---

## 🛠 兼容性与底层架构浅析

| 端 | 最低兼容系统 | 核心技术栈 | 核心取舍 |
|---|---|---|---|
| 服务端 (Java) | Android 2.2 (API 8) | 纯 Java + NanoHTTPD + Socket + SQLite | 锁定 targetSdk 22 跳过动态权限，极限兼容。 |
| 手机端 | Android 5.0 (API 21) | Java + Material Design 3 + OkHttp | 强制动态取色，适配 Android 13+ 权限。 |
| 桌面端 / CLI | Win 7 / Linux | Python 3 + Tkinter / cmd | 无内置浏览器，秒开不卡顿。 |
| 苹果 / 鸿蒙 | iOS 15 / Harmony 4 | SwiftUI / ArkTS | 原生声明式 UI，系统级 Want/OpenURL 调用。 |
| 次世代 (.NET) | Android 10+ / Win 10+ | C# + MAUI + ASP.NET Core | 现代存储权限模型，原生跨平台 GUI。 |

- **版本号控制**：项目引入了 `sync_version.py`。Actions 会自动读取 `CHANGELOG.md` 的版本号，注入到所有端的工程配置中。
- **纯手写协议栈**：WebDAV 与 FTP 完全手写 Socket 报文解析，不依赖重型库，保证极低包体积。

---

## ⚠️ 架构局限性与风险提示

1. **极寒模式副作用**：为了极致省电，开启极寒模式会强制关闭 LocalSend、内网穿透和 DLNA，仅保留核心传输。
2. **Root 模式的危险性**：全盘模式与 Web Shell 拥有极高的系统权限。在网页端操作 `/system` 等目录时请极其谨慎，严禁执行 `rm -rf /` 等破坏性指令。
3. **多用户共享**：请妥善保管 `admin` 账号，普通用户被限制在自己的根目录下，但仍需注意局域网明文传输风险。
4. **明文传输**：目前局域网主要基于 HTTP 传输。若在公共网络使用，请自建 SSH 穿透加密，或开启自签 HTTPS 框架。

---

## 协议

本项目使用 [MIT License](LICENSE)。