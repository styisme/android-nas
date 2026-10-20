# Android NAS (Server & Web & Client & TV & Watch & Desktop & CLI)

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com/)
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![Min SDK TV](https://img.shields.io/badge/TV_Min_SDK-API_19-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> **把吃灰的 Android 手机变成全平台私有云控制中枢。**
> 本项目不仅是一个轻量级的文件服务器，而是一个包含了服务端、网页端、手机客户端、电视盒子端、智能手表端、桌面 GUI 以及极客 CLI 的**全平台生态闭环**。
> 
> 原生内置 WebDAV、FTP、LocalSend、DLNA 四大主流局域网协议；支持跨端协同投屏与配置秒下发；手机端支持相册自动增量备份；Web 端与 CLI 端支持局域网剪贴板同步；在 Root 特权加持下，更能解锁系统底层目录（`/data`, `/system`）访问、Web Shell 终端执行以及 OOM 免死级保活。

---

## 📚 全局文档矩阵 (Documentation)

本项目拥有极其详尽的文档体系，无论你是小白还是极客，都能找到你需要的说明书：

- 📖 **[终极使用与运维指南 (USAGE.md)](docs/USAGE.md)**：
  从零部署、网络映射（WebDAV/FTP）、跨端协同配置、相册备份实操、CLI 终端指令大全以及深度故障排查。这是你上手 NAS 的第一站。
- 🔌 **[开发者 API 接口规范 (API_DOC.md)](docs/API_DOC.md)**：
  包含服务端 8080 端口的所有 HTTP API 细节（上传、下载、流媒体、鉴权）、WebDAV 底层协议规范、跨端协同 UDP 协议以及特殊端口（LocalSend 等）分配。
- 🏗️ **[底层架构深度解析 (ARCHITECTURE.md)](docs/ARCHITECTURE.md)**：
  深入剖析纯 Socket 手写协议栈、极限兼容 API 8 的妥协之道、全盘突破机制以及由无声音频、AlarmManager、双进程和 Root 注入构成的四层保活矩阵。
- 🛡️ **[安全策略与高危警告 (SECURITY.md)](SECURITY.md)**：
  **（高危必读）** 使用全盘模式、Web Shell 和公网穿透前，请务必阅读此文档，了解明文传输风险与防变砖守则。
- 🤝 **[开发者贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md)**：
  AI 生成代码的声明、深度项目结构图、JDK 编译环境隔离说明、代码提交规范（Conventional Commits）。
- 🤖 **[AI 生成声明 (AI_DISCLOSURE.md)](docs/AI_DISCLOSURE.md)**：
  关于本项目 99% 代码由大模型生成的真实开发流程与局限性声明。
- 📝 **[版本更新日志 (CHANGELOG.md)](CHANGELOG.md)**：
  追溯项目从 v1.0.0 到 v3.3.0 的完整演进史。

---

## 🚨 最重要：关于下载与发版机制

由于本项目采用极其激进的 CI/CD 自动化迭代，GitHub 右侧的 Releases 页面往往滞后于最新代码。**请直接前往 GitHub Actions 页面下载最新构建产物！**

👉 **[点此进入 Actions 获取最新版构建产物](https://github.com/styisme/android-nas/actions)**
*(进入页面后，选择顶部最新一次的绿色构建记录，划到页面最底部，下载对应的 `Artifacts` 压缩包即可)*

> **💡 极客一键全量发版模式**：
> 本项目配置了双轨制 CI/CD：
> 1. **日常改代码**：只触发对应端的快速独立编译。
> 2. **全量发版**：每次修改根目录 `CHANGELOG.md` 并推送，CI 会瞬间启动 `build-all.yml` 矩阵，将 **7 个端**全部并行编译，最终打成一个名为 `android-nas-all-vX.Y.Z.zip` 的单一压缩包，真正的“一键发版”。

---

## 🌟 为什么选择本项目？

市面上的手机 NAS 软件往往存在各种妥协：要么不支持老旧系统，要么 UI 落后，或者收费昂贵。本项目专为极客打造，具备以下碾压级优势：

1. **七端合一，无缝流转**：服务端、网页端、手机端、电视端、手表端、桌面端、CLI 命令行端全部覆盖。打破生态壁垒。
2. **多协议制霸**：原生实现 WebDAV、FTP、LocalSend、DLNA。在开启 Root 全盘模式后，这些协议可直接穿透 Android 沙盒访问 `/data` 与 `/system`。
3. **极客特权（Root/ADB）**：支持 OOM 免死注入、静音音频保活、Web Shell 终端。可以直接在网页端修改端口、切换密码与启停服务。
4. **极致兼容与美观并存**：服务端最低兼容十年前的 Android 2.2 (API 8)；手机端和网页端全面采用极致的 Material Design 3 规范，支持动态取色与无缝交互。

---

## ✨ 各端详细功能矩阵

### 📱 服务端 (Server - 装在闲置手机)
- **多端口并发调度**：底层开启多个线程池统一调度。
  - `8080`：Web 网页管理 / JSON API / 流媒体分片。
  - `8081`：标准 WebDAV 协议（**纯 Socket 手写实现**，非第三方库）。
  - `2121`：原生 FTP 服务（支持 PASV 被动模式）。
  - `53317`：LocalSend 接收端（免装 App 互传）。
  - `8090`：跨端协同发现服务（UDP）。
- **全盘 Root 模式**：一键突破 Android 沙盒，直接浏览修改底层文件，并在网页端自带 Web Shell 控制台（内置 `rm -rf` 等高危指令拦截）。
- **无敌四层保活矩阵**：
  - `Layer 1`：`WakeLock` + 前台服务 + `START_STICKY`。
  - `Layer 2`：`SilentPlayer` 循环写入 0 字节 PCM，伪装音乐播放。
  - `Layer 3`：`AlarmManager` 每 5 分钟心跳唤醒，被杀秒拉活。
  - `Layer 4`：Root/ADB 注入 `oom_score_adj=-1000` 获得免死金牌，并强杀系统级杀后台进程。

### 🌐 Web 网页端 (MD3 Web)
- **UI 革命**：全面采用 Material Design 3 规范，操作全部收纳进 Bottom Sheet 底部弹窗，完美适配手机与电脑。
- **系统控制台**：无需碰手机，在网页右上角即可直接修改密码、修改端口、开关各项协议。
- **401 鉴权拦截**：全局 Fetch 拦截自动弹出密码框，彻底告别白屏。
- **流媒体与工具**：支持 `Range` 拖动播放、ZIP 在线解压、局域网剪贴板同步、文件免密分享短链。

### 🖥️ 手机客户端 (Client - Android 5.0+)
- **相册自动增量备份**：利用 `MediaStore` 扫描本地，比对 NAS `/Photos/` 目录，只传新照片，杜绝浪费流量。
- **局域网一键扫描**：多线程并发探测 `8080` 端口，免去手动输入 IP 的烦恼。
- **跨端协同**：一键下发配置到电视、一键将手机视频投屏到电视。
- **批量处理**：支持全选、批量下载、批量删除。

### 📺 TV 电视端 (TV - Android 4.4+)
- **Leanback 卡片流**：4 列网格布局，遥控器焦点移动时带有 1.1 倍放大动画，沉浸式大屏体验。
- **极速启动**：纯原生编写，无任何第三方 UI 库，老电视盒子秒开。
- **虚拟全键盘**：边打边搜，完美解决遥控器输入痛点。

### ⌚ 手表端 (Watch - Android 5.0+)
- **专属卡片布局**：RecyclerView 与按压动画，动态适配圆盘屏幕内边距。
- **文件操作**：支持手表直接预览媒体，支持下载文件至手表专属沙盒。

### 💻 桌面端 (Desktop - Win/Linux)
- **纯原生 Tkinter**：无内置浏览器，秒开不卡顿。
- **多选并发上传**：支持一次性上传多个文件，自带在线文本编辑与 ZIP 解压。

### ⌨️ CLI 命令行端 (Win/Linux)
- **极客终端**：支持 `ls`、`cd`、`get`、`put`、`find`、`unzip`、`share` 等操作。
- **交互式 Shell**：输入 `shell` 进入，支持 Tab 路径自动补全。
- **远程控制**：支持 `exec` 直接调用服务端 Root 命令。

---

## 🔗 跨端协同与黑科技玩法

本项目实现了真正的局域网无缝协同，完全不需要连接外网：

1. **一键下发配置**：手机端点击“发送配置到电视”，通过 UDP 发现电视并下发 NAS 密码，电视端自动完成登录。
2. **手机/手表投屏**：在文件列表点击视频，发送 Play 指令，电视端瞬间拉起播放。
3. **Web 剪贴板同步**：手机复制文字，网页端直接粘贴。

---

## 🛠 兼容性与底层架构浅析

| 端 | 最低兼容系统 | 核心技术栈 | 核心取舍 |
|---|---|---|---|
| 服务端 | Android 2.2 (API 8) | 纯 Java + NanoHTTPD + Socket | 锁定 targetSdk 22 跳过动态权限，极限兼容。 |
| 手机端 | Android 5.0 (API 21) | Java + Material Design 3 + OkHttp | 强制动态取色，适配 Android 13+ 权限。 |
| TV 端 | Android 4.4 (API 19) | 纯原生 Android + RecyclerView | 零第三方 UI 库，极速启动。 |
| 手表端 | Android 5.0 (API 21) | 纯原生 Android + RecyclerView | 动态圆盘 Padding 适配。 |
| 桌面端 / CLI | Win 7 / Linux | Python 3 + Tkinter / cmd | 无内置浏览器，秒开不卡顿，支持 Tab 补全。 |

- **版本号控制**：项目引入了 `sync_version.py`。Actions 会自动读取 `CHANGELOG.md` 的版本号，注入到 7 个端的工程配置中，彻底告别手动改版本号。
- **纯手写协议栈**：WebDAV 与 FTP 完全手写 Socket 报文解析，不依赖 Apache Commons 等重型库，保证了极低的包体积。

---

## ⚠️ 架构局限性与风险提示

1. **极寒模式副作用**：为了极致省电，开启极寒模式会强制关闭 LocalSend、内网穿透和 DLNA，仅保留核心传输。
2. **Root 模式的危险性**：全盘模式与 Web Shell 拥有极高的系统权限。在网页端操作 `/system` 等目录时请极其谨慎，严禁执行 `rm -rf /` 等破坏性指令。
3. **明文传输**：目前局域网主要基于 HTTP 传输。若在公共网络使用，请自建 SSH 穿透加密，或开启自签 HTTPS 框架。

---

## 协议

本项目使用 [MIT License](LICENSE)。