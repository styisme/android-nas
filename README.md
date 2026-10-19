# Android NAS (Server & Web & Client & TV & Watch & Desktop)

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com/)
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![Min SDK TV](https://img.shields.io/badge/TV_Min_SDK-API_19-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> 将你的任意一台闲置 Android 设备变成全能私有云中心。本项目不仅是一个简单的文件服务器，而是一个包含网页端、手机端、电视端、手表端与电脑端，内置 WebDAV、FTP、LocalSend、DLNA 以及跨端协同投屏的**全平台生态闭环**。

---

## 🚨 重要：关于下载与版本获取

**由于本项目采用极其激进的 CI/CD 自动化迭代，GitHub 右侧的 Releases 页面往往滞后于最新代码。**
如果你发现 Releases 里的版本较旧，或者你急需体验最新打包的版本，**请直接前往 GitHub Actions 页面下载最新构建产物！**

👉 **[点此进入 Actions 获取最新版构建产物](https://github.com/styisme/android-nas/actions)**
*(进入页面后，选择顶部最新一次的绿色构建记录，划到页面最底部，下载对应端的 `Artifacts` 压缩包即可)*

---

## ⚠️ 关于 AI 生成声明

> **本项目由 AI 辅助生成。**
> 
> 绝大多数代码（包括服务端底层架构、原生 WebDAV/FTP/DLNA 协议栈、LocalSend 接收端、跨端协同发现协议、所有端的 UI 布局与自动化构建脚本）由大型语言模型（LLM）在开发者的指导下生成。经过人工多次真机测试、编译排错与边缘场景修正，最终呈现出极其稳定的产品级状态。
> 
> 详细说明请见 [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md)。

---

## 🌟 为什么选择本项目？（核心优势）

市面上的手机 NAS 软件往往存在各种妥协：要么不支持老系统，要么没有现代化 UI，或者仅支持单一的文件传输协议。本项目专为极客与实用主义者打造，具备以下碾压级优势：
1. **全平台生态覆盖，无缝流转**：不仅仅是服务端，我们将手机客户端、电视盒子端、智能手表端、桌面端全部补齐，让数据在你所有的屏幕之间自由穿梭。
2. **多协议制霸**：服务端原生实现了 WebDAV、FTP、LocalSend、DLNA 四大主流协议。无论是映射网络驱动器、老电视投屏还是手机互传，通通支持。
3. **极致的性能与省电平衡**：专为挂机旧手机设计了“极寒省电模式”。一键降级 Wi-Fi 锁频、强制切断高耗电服务，仅保留核心数据传输，极限延长电池寿命。
4. **极限兼容与现代体验并存**：服务端兼容十年前的 Android 2.2 (API 8)，而手机端则采用了当下最新的 Material Design 3 与动态取色（Material You）。

---

## ✨ 各端详细功能矩阵

### 📱 服务端 (Server - 装在闲置手机)
- **多端口并发服务**：
  - `8080` 端口：Web 网页管理 / JSON API / 流媒体分片。
  - `8081` 端口：标准 WebDAV 协议（纯 Socket 实现）。
  - `2121` 端口：原生 FTP 服务（支持被动模式 PASV）。
  - `53317` 端口：LocalSend 接收端（免装 App 互传）。
  - `8090` 端口：跨端协同发现服务（UDP）。
- **服务端驾驶舱**：可随时修改设备别名、自定义各服务端口、修改管理员密码、一键开关各项服务。
- **极寒省电模式**：开启后，强制降级 Wi-Fi 芯片休眠策略，自动关闭内网穿透与 LocalSend，保障极限续航。
- **安全防护**：Basic Auth 鉴权、防路径穿越校验、Guest 访客权限隔离（仅限 Public 目录）、LocalSend PIN 码防爆破。

### 🌐 Web 网页端 (MD3 Web)
- **拖拽上传**：全区域拖拽文件，带真实百分比进度条。
- **全局搜索**：全盘递归搜索文件。
- **代码在线预览与编辑**：内置 highlight.js，支持 `.java`, `.json`, `.md` 等代码高亮，保存即写回手机。
- **流媒体播放**：支持 MP4/MP3 在线播放，支持 Range 请求拖动进度条。
- **一键免密分享**：生成带 UUID Token 的短链接，外部用户无需登录即可直接下载。
- **局域网剪贴板**：网页端与客户端无缝同步文本。
- **ZIP 在线解压**：无需下载，直接在服务端解压。

### 🖥️ 手机客户端 (Client - Android 5.0+)
- **多协议智能路由**：添加设备时可选择原生 JSON、WebDAV 或 FTP 协议。
- **相册自动备份**：一键扫描本地相册，全量增量上传至 NAS 的 `/Photos/`。
- **网格瀑布流**：支持列表与多列网格视图切换，长按多选批量删除。
- **全量文件管理**：下载（带进度条）、重命名、属性查看、新建文件夹。
- **跨端协同**：一键下发配置到电视、一键将视频投屏到电视。

### 📺 TV 电视端 (TV - Android 4.4+)
- **极简遥控器交互**：纯原生无第三方库，老旧电视盒子秒开。
- **哔哩哔哩同款搜索**：全屏搜索层，自带虚拟全键盘，边打边搜。
- **原生视频解码**：内置原生播放器，完美支持电视硬解拉流。
- **被动协同**：接收手机的配置下发与投屏指令，瞬间全屏播放。

### ⌚ 手表端 (Watch - Android 5.0+)
- **自适应圆盘/方盘**：MD3 卡片风格 UI，动态计算屏幕内边距防遮挡。
- **多设备与多协议**：支持原生、WebDAV、FTP，可输入账号密码。
- **腕上遥控器**：点击视频，瞬间投屏至客厅电视。

### 💻 桌面端 (Desktop - Win/Linux)
- 基于 Python + Tkinter，无边框轻量级，绿色免安装。
- 支持全套文件浏览、下载与上传。

---

## 🔗 跨端协同 (黑科技玩法)

本项目实现了真正的局域网无缝协同，完全不需要连接外网：
1. **一键下发配置**：手机端右上角点击“发送配置到电视”，通过 UDP 发现电视并下发 NAS 密码，电视端自动完成登录，无需用遥控器敲密码。
2. **手机/手表投屏**：在手机或手表的文件列表点击视频，发送 Play 指令，电视端瞬间拉起播放。
3. **剪贴板同步**：手机复制文字，网页端直接粘贴。

---

## 🛠 全自动发版机制与兼容性矩阵

### 兼容性一览

| 端 | 最低兼容系统 | 目标兼容系统 | 核心技术栈 |
|---|---|---|---|
| 服务端 | Android 2.2 (API 8) | Android 5.1 (API 22) | 纯 Java + NanoHTTPD + Socket |
| 手机端 | Android 5.0 (API 21) | Android 13 (API 33) | Java + Material Design 3 + OkHttp |
| TV 端 | Android 4.4 (API 19) | Android 13 (API 33) | 纯原生 Android |
| 手表端 | Android 5.0 (API 21) | Android 13 (API 33) | 纯原生 Android |
| 桌面端 | Win 7 / Linux | Win 11 / Linux 最新 | Python 3 + Tkinter |

### 全自动发版
项目引入了 `sync_version.py`。Actions 在编译前会读取 `CHANGELOG.md` 的最新版本号，自动注入到所有端的 `build.gradle` 中，动态修改 `versionCode` 与 `versionName`，彻底告别手动修改版本号的繁琐。

---

## 📡 服务端 API 接口文档 (8080端口)

*注：所有请求需携带 `Authorization: Basic Base64(账号:密码)`。*

| 接口 | 方法 | 参数 | 说明 |
|---|---|---|---|
| `/api/list` | `GET` | `path` | 获取目录文件列表（JSON） |
| `/api/search` | `GET` | `q` | 全局递归搜索文件 |
| `/api/download` | `GET` | `path` | 下载文件/流媒体分片 |
| `/api/preview` | `GET` | `path` | 获取文本内容 |
| `/api/upload` | `POST` | `path`, `file` | 表单多文件上传 |
| `/api/mkdir` | `POST` | `path`, `name` | 新建文件夹 |
| `/api/delete` | `POST` | `path` | 递归删除 |
| `/api/rename` | `POST` | `path`, `newName` | 重命名 |
| `/api/save` | `POST` | `path`, `content` | 保存在线编辑内容 |
| `/api/clipboard` | `GET/POST` | `content` | 局域网剪贴板 |
| `/api/unzip` | `POST` | `path` | 在线解压 ZIP |
| `/api/share` | `POST` | `path` | 生成免密分享 Token |
| `/*` (任意路径) | `PUT` | 文件流 | 客户端/WebDAV 上传 |

---

## 协议

本项目使用 [MIT License](LICENSE)。