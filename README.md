# Android NAS (Server & Web & Client & TV & Watch & Desktop & CLI)

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com/)
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![Min SDK TV](https://img.shields.io/badge/TV_Min_SDK-API_19-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> **把吃灰的 Android 手机变成全平台私有云控制中枢。**
> 本项目不仅是一个轻量级的文件服务器，而是一个包含了服务端、网页端、手机客户端、电视盒子端、智能手表端、桌面 GUI 以及极客 CLI 的**全平台生态闭环**。
> 
> 原生内置 WebDAV、FTP、LocalSend、DLNA 四大主流局域网协议；支持跨端协同投屏与配置秒下发；手机端支持相册自动增量备份；Web 端与 CLI 端支持局域网剪贴板同步；在 Root 特权加持下，更能解锁系统底层目录（/data, /system）访问、Web Shell 终端执行以及 OOM 免死级保活。

---

## 🚨 最重要：关于下载与版本获取

由于本项目采用极其激进的 CI/CD 自动化迭代，GitHub 右侧的 Releases 页面往往滞后于最新代码。如果你发现 Releases 里的版本较旧，或者急需体验最新打包的版本，**请直接前往 GitHub Actions 页面下载最新构建产物！**

👉 **[点此进入 Actions 获取最新版构建产物](https://github.com/styisme/android-nas/actions)**
*(进入页面后，选择顶部最新一次的绿色构建记录，划到页面最底部，下载对应的 `Artifacts` 压缩包即可)*

> **💡 极客一键发版模式**：
> 我们现已支持单次全量发版。每次修改根目录的 `CHANGELOG.md` 并推送，CI 会自动解析版本号，并将 **7 个端**（Server/Client/TV/Watch/PC GUI/CLI/Web）全部并行编译，最终打成一个名为 `android-nas-all-vX.Y.Z.zip` 的单一压缩包，真正的“一键发版”。

---

## ⚠️ 关于 AI 生成声明

> **本项目 99% 以上的代码由 AI 辅助生成。**
> 
> 本项目开源的重要目的之一，就是展示在极端依赖 AI 的工作流下，能够诞生何等规模的工程。所有底层架构、原生 WebDAV/FTP/DLNA 协议栈、跨端协同发现协议、CLI 命令行交互、全平台 MD3 UI 以及自动化构建脚本，均由大型语言模型（LLM）在开发者的指令下直接输出。
> 
> 人类开发者在此过程中担任“产品经理”与“测试工程师”，负责需求决策、在 Termux 中执行编译脚本、使用 Mgit 提交代码，并在真机测试中将报错截图反馈给 AI 进行修复。
> 
> 详细声明请见 [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md)。

---

## 🌟 为什么选择本项目？（核心优势）

市面上的手机 NAS 软件往往存在各种妥协：要么不支持老旧系统，要么没有现代化 UI，或者仅支持单一的文件传输协议，亦或是收费昂贵、广告满天飞。本项目专为极客与实用主义者打造，具备以下碾压级优势：

1. **七端合一，无缝流转**：服务端、网页端、手机端、电视端、手表端、桌面端、CLI 命令行端全部覆盖。数据在你所有的屏幕间自由穿梭，打破生态壁垒。
2. **多协议制霸**：原生实现 WebDAV、FTP、LocalSend、DLNA 四大主流协议。在开启 Root 全盘模式后，这些协议可直接穿透 Android 沙盒访问 `/data` 与 `/system`。
3. **极客特权（Root/ADB）**：支持 OOM 免死注入（`oom_score_adj=-1000`）、双进程守护、静音音频保活、Web Shell 终端。甚至可以直接在网页端修改端口、切换密码与启停各项底层服务。
4. **极致的兼容性**：服务端最低兼容 Android 2.2 (API 8)，十年前的破旧手机也能焕发第二春；而手机端和网页端则全面采用了极致的 Material Design 3 设计规范，美观且现代。

---

## ✨ 各端详细功能矩阵

### 📱 服务端 (Server - 装在闲置手机)
- **多端口并发服务**：核心统一调度，底层开启多个线程池。
  - `8080`：Web 网页管理 / JSON API / 流媒体分片。
  - `8081`：标准 WebDAV 协议（纯 Socket 实现）。
  - `2121`：原生 FTP 服务（支持 PASV 被动模式）。
  - `53317`：LocalSend 接收端（免装 App 互传）。
  - `8090`：跨端协同发现服务（UDP）。
- **全盘 Root 模式**：一键突破 Android 沙盒，直接浏览修改底层文件，并在网页端自带 Web Shell 控制台。
- **无敌保活矩阵**：WakeLock、前台服务、静音音频欺骗、AlarmManager 心跳、Root OOM 免死注入多重保障，专为挂机旧手机设计。
- **UI 重构**：主界面卡片化，驾驶舱引入 Tab 分页（基础、服务、高级），彻底告别拥挤的复选框。

### 🌐 Web 网页端 (MD3 Web)
- **UI 重构**：全面采用 Material Design 3 规范。操作全部收纳进 Bottom Sheet 底部弹窗，完美适配手机与电脑浏览器。
- **系统控制台**：点开右上角菜单，即可直接修改密码、修改端口、开关各项协议。
- **401 鉴权拦截**：彻底解决开启密码验证后页面白屏的问题，全局 Fetch 拦截自动弹窗。
- **流媒体与工具**：支持 Range 拖动播放、ZIP 在线解压、局域网剪贴板同步、文件免密分享短链。

### 🖥️ 手机客户端 (Client - Android 5.0+)
- **相册自动备份**：利用 `MediaStore` 扫描本地，比对 NAS `/Photos/` 目录进行增量上传，一键同步所有回忆，杜绝重复浪费流量。
- **局域网一键扫描**：多线程并发探测局域网内开放 `8080` 端口的设备，免去手动输入 IP 的烦恼。
- **跨端协同**：一键下发 NAS 配置到电视、一键将手机视频投屏到电视。
- **多选批处理**：支持全选、批量下载、批量删除。

### 📺 TV 电视端 (TV - Android 4.4+)
- **Leanback 卡片流**：4 列网格布局，遥控器焦点移动时带有 1.1 倍放大动画，沉浸式大屏体验。
- **虚拟全键盘**：支持边打边搜，完美解决遥控器输入痛点。
- **极速启动**：纯原生编写，无任何第三方 UI 库，老电视盒子秒开。

### ⌚ 手表端 (Watch - Android 5.0+)
- **专属卡片布局**：全面改用 RecyclerView 与按压动画，动态适配圆盘屏幕内边距。
- **文件操作**：支持手表直接预览图片/视频，支持下载文件至手表专属沙盒。

### 💻 桌面端 (Desktop - Win/Linux)
- **纯原生 Tkinter**：扁平化现代 UI，无需安装庞大的浏览器内核（如 Electron），内存占用极低，秒开不卡顿。
- **多选并发上传**：支持一次性上传多个文件，自带在线文本编辑与 ZIP 解压。

### ⌨️ CLI 命令行端 (Win/Linux)
- **极客终端**：支持 `ls`、`cd`、`get`、`put`、`find`、`unzip`、`share` 等全套操作。
- **交互式 Shell**：输入 `shell` 进入交互模式，支持 Tab 路径自动补全。
- **远程控制**：支持在终端直接执行 `exec` 调用服务端 Root 命令。

---

## 🔗 跨端协同与黑科技玩法

本项目实现了真正的局域网无缝协同，完全不需要连接外网：

1. **一键下发配置**：手机端右上角点击“发送配置到电视”，通过 UDP 发现电视并下发 NAS 密码，电视端自动完成登录，无需用遥控器敲密码。
2. **手机/手表投屏**：在手机或手表的文件列表点击视频，发送 Play 指令，电视端瞬间拉起播放。
3. **Web 剪贴板同步**：手机复制文字，网页端直接粘贴。

---

## 🚀 快速开始：如何部署与使用？

### 1. 服务端部署 (一键起步)
1. 在旧手机上安装 `app-debug.apk`。
2. **授予必要权限**：首次打开会请求“所有文件访问权限”（Android 11+）和“忽略电池优化”，请务必允许。
3. **启动服务**：点击 App 内的启动按钮，记下首页显示的 IP 地址（如 `http://192.168.1.100:8080`）。
4. **开启全盘模式**：若手机已 Root，在驾驶舱【高级】页开启全盘模式，即可访问 `/data` 目录。

### 2. 客户端连接
1. 打开手机客户端，点击右上角扫描按钮，自动发现 NAS 并添加。
2. 进入设备，即可进行文件管理、相册备份、视频投屏等操作。

### 3. CLI 极客体验
```bash
# 下载 Actions 里的 nas-cli-linux 后
chmod +x nas-cli-linux
./nas-cli-linux
# 按照彩色向导输入 NAS IP 和账号密码
# 自动进入交互式终端
nas> cd /Movies
nas> find mp4
nas> edit config.txt
nas> exec top
```

---

## 🛠 全自动发版机制与兼容性矩阵

### 兼容性一览
| 端 | 最低兼容系统 | 核心技术栈 | 核心取舍 |
|---|---|---|---|
| 服务端 | Android 2.2 (API 8) | 纯 Java + NanoHTTPD + Socket | 锁定 targetSdk 22 跳过动态权限，极限兼容。 |
| 手机端 | Android 5.0 (API 21) | Java + Material Design 3 + OkHttp | 强制动态取色，适配 Android 13+ 权限。 |
| TV 端 | Android 4.4 (API 19) | 纯原生 Android + RecyclerView | 零第三方 UI 库，极速启动。 |
| 手表端 | Android 5.0 (API 21) | 纯原生 Android + RecyclerView | 动态圆盘 Padding 适配。 |
| 桌面端 | Win 7 / Linux | Python 3 + Tkinter | 无内置浏览器，秒开不卡顿。 |
| CLI端 | Win 7 / Linux | Python 3 (cmd 模块) | 全量文件操作，支持 Tab 补全。 |

### 全自动发版 (CI/CD)
项目引入了 `sync_version.py`。Actions 在编译前会读取 `CHANGELOG.md` 的最新版本号，自动注入到所有端的工程配置中，彻底告别手动修改版本号的繁琐。
*   **日常改代码**：只触发对应端的独立编译（已配置忽略 `.md` 修改）。
*   **改 `CHANGELOG.md`**：触发全量打包，最终输出一个包含所有端的 `.zip` 压缩包。

---

## ⚠️ 架构局限性与风险提示

1. **极寒模式副作用**：为了极致省电，开启极寒模式会强制关闭 LocalSend、内网穿透和 DLNA，仅保留核心传输。
2. **Root 模式的危险性**：全盘模式与 Web Shell 拥有极高的系统权限。在网页端操作 `/system` 等目录时请极其谨慎，严禁执行 `rm -rf /` 等破坏性指令（系统已有基础防误触）。
3. **明文传输**：目前局域网主要基于 HTTP 传输。若在公共网络使用，请自建 SSH 穿透加密，或开启自签 HTTPS 框架。
4. **低内存设备保活**：前台服务能降低被回收概率，但在极低内存设备上仍可能被杀。

---

## 协议

本项目使用 [MIT License](LICENSE)。