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
  追溯项目从 v1.0.0 到 v4.0.0 的完整演进史。

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
4. **极致兼容与美观并存**：服务端最低兼容十年前的 Android 2.2 (API 8)；手机端和网页端全面采用极致的 Material Design 3 规范。
5. **多核架构进化**：保留了对上古设备极致兼容的 Java 主干，同时孵化 .NET 10 跨平台分支与 HarmonyOS 纯血原生分支。

---

## ✨ 十端详细功能矩阵

### 📱 服务端 (Server - 装在闲置手机)
- **多端口并发调度**：
  - `8080`：Web 网页管理 / JSON API / 流媒体分片。
  - `8081`：标准 WebDAV 协议（**纯 Socket 手写实现**，非第三方库）。
  - `2121`：原生 FTP 服务（支持 PASV 被动模式）。
  - `53317`：LocalSend 接收端（免装 App 互传）。
  - `8090`：跨端协同发现服务（UDP）。
  - `8082`：WebSocket 远控服务（H264 推流与触控指令）。
- **全盘 Root 模式**：一键突破沙盒，直接浏览修改底层文件，并在网页端自带 Web Shell 控制台。
- **无敌四层保活矩阵**：WakeLock、静音音频欺骗、AlarmManager 心跳唤醒、Root/ADB 注入 `oom_score_adj=-1000` 免死金牌。
- **驾驶舱重构**：MD3 卡片化 UI，基础/服务/高级 Tab 分页。

### 🌐 Web 网页端 (MD3 Web)
- **多模态远程桌面**：H264 硬件编码 30fps 推流 + 免授权 Root 截图降级方案，支持双引擎触控反向控制。
- **系统控制台**：无需碰手机，在网页右上角即可直接修改密码、修改端口、开关各项协议。
- **UI 革命**：全面采用 Material Design 3 规范，401 鉴权全局拦截，操作全部收纳进 Bottom Sheet。
- **流媒体与工具**：支持 `Range` 拖动播放、ZIP 在线解压、剪贴板同步、免密分享短链、Web 终端。

### 🖥️ 手机客户端 (Client - Android 5.0+)
- **相册自动增量备份**：利用 `MediaStore` 扫描本地，比对 NAS `/Photos/` 目录，只传新照片。
- **局域网一键扫描**：多线程并发探测，免去手动输入 IP。
- **远控与控制台**：引入原生 `WebViewActivity`，一键全屏打开网页端的终端与远控面板。
- **分享外链**：文件菜单新增“生成分享外链”，直接调起系统分享面板。

### 💻 桌面端 (Desktop - Win/Linux)
- **纯原生 Tkinter**：无内置浏览器，秒开不卡顿。
- **全局搜索与剪贴板**：顶部新增全局搜索框与局域网剪贴板同步弹窗。
- **一键复制外链**：选中文件即可生成免密短链并自动复制到电脑剪贴板。
- **在线编辑与解压**：多选并发上传，自带在线文本编辑与 ZIP 解压。

### ⌨️ CLI 命令行端 (Win/Linux)
- **交互式 Shell**：输入 `shell` 进入，支持 Tab 路径自动补全。
- **全指令支持**：支持 `ls`、`cd`、`get`、`put`、`find`、`unzip`、`share`。
- **远程控制**：支持 `edit` 调起本地编辑器写回文件；支持 `exec` 直接调用服务端 Root 命令。

### 🍎 苹果端 (SwiftUI)
- 原生编写，极简设计。全端对齐了全局搜索、局域网剪贴板同步、系统级控制台唤起功能。
- 支持 iOS 15+ / macOS 12+ 以及原生控件风格。

### 🟦 鸿蒙端 (ArkTS) - [独立仓库](https://github.com/styisme/HarmonyNAS)
- **纯血原生**：采用 ArkUI 声明式开发，一套代码自适应手机、折叠屏、平板与 PC。
- **拒绝黑科技保活**：依托官方长时任务 (`backgroundTaskManager`) 合法驻留后台。
- **安全沙箱**：数据锁定应用沙盒，内置防路径穿越拦截器，绝对安全。

### 📺 TV & ⌚ 手表端
- **TV 端**：Leanback 卡片流 4 列网格，遥控器焦点移动 1.1 倍放大动画，虚拟全键盘边打边搜。
- **手表端**：RecyclerView 卡片布局，按压动画。支持媒体预览与下载至手表专属沙盒。

### 📱 微信小程序端
- **免安装即用**：支持全盘文件搜索、局域网剪贴板浮层、以及生成免密分享短链并自动复制。

---

## 🔗 跨端协同与黑科技玩法

本项目实现了真正的局域网无缝协同，完全不需要连接外网：

1. **一键下发配置**：手机端点击“发送配置到电视”，通过 UDP 发现电视并下发 NAS 密码，电视端自动完成登录。
2. **手机/手表投屏**：在文件列表点击视频，发送 Play 指令，电视端瞬间拉起播放。
3. **全端剪贴板同步**：手机复制文字，网页端、桌面端直接粘贴。

---

## 🛠 兼容性与底层架构浅析

| 端 | 最低兼容系统 | 核心技术栈 | 核心取舍 |
|---|---|---|---|
| 服务端 (Java) | Android 2.2 (API 8) | 纯 Java + NanoHTTPD + Socket | 锁定 targetSdk 22 跳过动态权限，极限兼容。 |
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
3. **明文传输**：目前局域网主要基于 HTTP 传输。若在公共网络使用，请自建 SSH 穿透加密，或开启自签 HTTPS 框架。

---

## 协议

本项目使用 [MIT License](LICENSE)。