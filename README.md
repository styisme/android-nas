# Android NAS (Server & Client & Desktop)

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com/)
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> 把任何一台闲置的 Android 手机变成局域网私有云 NAS。支持 MD3 网页端、Android 客户端、WebDAV 协议，更内置了 **LocalSend 免装接收**、**全自动内网穿透**与**极寒省电模式**！

---

## 🚨 重要：关于下载与版本获取

**由于项目更新迭代极快，GitHub 右侧的 Releases 页面可能存在更新延迟。**
如果你发现 Releases 里的版本较旧，或者你急需体验最新打包的版本，**请直接前往 GitHub Actions 页面下载最新构建产物！**

👉 **[点此进入 Actions 获取最新版构建产物](https://github.com/styisme/android-nas/actions)**
*(选择顶部最新一次的绿色构建记录，划到页面最底部，下载 `android-nas-server` 与 `nas-client` 压缩包即可)*

---

## ⚠️ 关于 AI 生成声明

> **本项目由 AI 辅助生成。**
> 
> 绝大多数代码（包括服务端架构、原生 WebDAV 协议栈、LocalSend 接收端、内网穿透引擎、MD3 网页端、Android 客户端 UI 与网络逻辑、Python 桌面端及自动化构建脚本）由大型语言模型（LLM）在开发者指导下生成，并经过人工多次真机测试、编译排错与修正。
> 
> 详细说明请见 [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md)。

---

## 🌟 为什么选择本项目？

市面上大部分旧手机 NAS 应用要么不支持现代系统，要么缺乏安全防护，更别提跨平台和免装接收了。本项目专为极客与实用主义者打造，具备以下核心优势：
1. **极限兼容与现代体验并存**：服务端最低支持 **Android 2.2 (API 8)**，让十年前的废置手机焕发第二春；而客户端与 Web 端则采用当下最新的 **Material Design 3 (MD3)** 设计规范，支持动态取色。
2. **全平台与多协议生态覆盖**：服务端原生实现了 **WebDAV** 协议、**LocalSend v2.2** 接收协议以及 JSON API。Windows/macOS 也能直接将其映射为本地网络驱动器。
3. **内网穿透免配置**：内置 7 大免注册穿透节点，一键生成 HTTPS 公网链接；同时保留高级 SSH 穿透供硬核玩家使用。
4. **极寒省电模式**：针对长期挂机的旧手机，一键降级 Wi-Fi 锁频率、关闭高耗电服务（LocalSend/穿透），仅保留核心 Web 与 WebDAV，让旧手机安静且省电地当 NAS。

---

## ✨ 详细功能矩阵

### 📱 服务端 (Server - 装在闲置手机)
- **多端口并发服务**：
  - `8080` 端口：提供极速 Web 网页端管理与 JSON API。
  - `8081` 端口：提供标准 WebDAV 网络邻居（支持 Windows/macOS 映射）。
  - `53317` 端口：**LocalSend 接收端**（免装 App，官方客户端直接投递文件）。
- **服务端驾驶舱**：修改设备别名、自定义 Web/WebDAV 端口、修改管理员密码、开/关流媒体与剪贴板、一键重启服务。
- **边缘化稳定性**：断网守护重连、防路径穿越校验、防 PIN 码爆破、同名文件自动重命名防覆盖。
- **保活机制**：前台服务常驻通知 + `START_STICKY` + `WakeLock` + `WifiLock`，配合开机自启。

### 🌐 Web 网页端 (MD3 Web)
- **拖拽上传**：全区域拖拽文件，带真实百分比进度条。
- **代码预览**：内置 highlight.js，支持多语言代码高亮与在线编辑。
- **流媒体播放**：支持 MP4/MP3 在线播放（支持 Range 分片请求与拖动进度条）。
- **在线解压**：支持对 `.zip` 文件进行一键在线解压。
- **局域网剪贴板**：支持与客户端实时同步文本内容。
- **免密分享**：一键生成带 UUID Token 的短链接，外部用户免登录直接下载。

### 🖥️ Android 客户端 (Client - 装在主力手机)
- **双协议智能路由**：原生 JSON 接口管理自家 NAS，WebDAV 协议兼容群晖等外部设备。
- **相册自动备份**：一键将手机相册全量增量备份至 NAS 的 `/Photos/` 目录。
- **网格瀑布流视图**：一键切换多列网格视图浏览图片。
- **全量文件操作**：点击文件弹出菜单（预览/播放、下载、重命名、删除、属性）；长按文件进入多选模式支持批量删除。
- **自动化发现**：局域网并发扫描、支持扫描服务端生成的二维码直接连接。

### 💻 桌面端 (Desktop - Win/Linux)
- 基于 Python + Tkinter 原生开发，内存占用极低。
- 支持文件浏览、上传、下载、新建文件夹、重命名等全套操作。
- 已配置 GitHub Actions，自动打包 Windows 的 `.exe` 和 Linux 的可执行文件。

---

## 🚀 快速上手与使用指南

请务必阅读极其详细的 **[使用说明书 (docs/USAGE.md)](docs/USAGE.md)**，里面包含了从安装、配置、到各种高级玩法的全流程。

### 极速体验步骤：
1. **部署服务端**：去 Actions 下载服务端 APK，安装在旧手机上，点击“启动服务”。
2. **获取地址**：手机屏幕上会显示 `http://局域网IP:8080` 以及默认的 LocalSend PIN 码（默认 `1234`）。
3. **网页管理**：在电脑或手机浏览器打开该地址，直接拖拽文件即可上传。
4. **客户端连接**：安装 Android 客户端，打开会自动扫描局域网内的 NAS，点击连接即可进入文件管理。

---

## 📡 服务端 API 接口文档 (8080端口)

*注：如果开启了“账号验证”开关，所有请求必须携带 `Authorization: Basic Base64(账号:密码)` 请求头。*

| 接口 | 方法 | 参数 | 说明 |
|---|---|---|---|
| `/api/list` | `GET` | `path` (当前路径) | 获取目录下的文件列表（返回 JSON） |
| `/api/search` | `GET` | `q` (关键字) | 全局递归搜索文件 |
| `/api/download` | `GET` | `path` (文件路径) | 下载文件（支持 Range 视频流分片） |
| `/api/preview` | `GET` | `path` (文件路径) | 获取文本文件内容（用于高亮/编辑） |
| `/api/upload` | `POST` | `path`, `file` | 网页端表单多文件上传 |
| `/api/mkdir` | `POST` | `path`, `name` | 新建文件夹 |
| `/api/delete` | `POST` | `path` | 递归删除文件或文件夹 |
| `/api/rename` | `POST` | `path`, `newName` | 重命名文件 |
| `/api/save` | `POST` | `path`, `content` | 保存在线编辑的文本内容 |
| `/api/clipboard` | `GET/POST` | `content` | 获取或设置局域网剪贴板 |
| `/api/unzip` | `POST` | `path` | 在线解压 Zip 文件 |
| `/api/share` | `POST` | `path` | 生成免密分享 Token |
| `/share/:token`| `GET` | - | 通过 Token 免密下载文件 |
| `/*` (任意路径) | `PUT` | 文件流 | 客户端/第三方 WebDAV 标准流式上传 |

---

## ❓ 常见问题排查 (FAQ)

1. **浏览器或客户端提示 `ERR_CONNECTION_REFUSED` (拒绝连接)？**
   - 查看服务端 App 首页是否显示了**红色错误提示**。若提示端口被占用，请前往控制中心更换 Web 或 WebDAV 端口。
   - 如果是在**同一台手机**上同时跑服务端和客户端测试，请将地址改为 `127.0.0.1:8080`。
   - 确保两台设备连在同一个 WiFi，且路由器未开启“AP隔离”。
2. **为什么手机息屏后，电脑就传不动文件了？**
   - 请进入手机系统设置，找到本应用，将电池策略设为**“无限制”**或**“允许后台高耗电”**。App 已经申请了唤醒锁，但部分定制 ROM（如 MIUI、HarmonyOS）需手动放行。
3. **LocalSend 搜索不到 NAS？**
   - 去服务端控制中心检查 LocalSend 功能是否开启。
   - 检查是否开启了“极寒模式”（开启后会强制关闭 LocalSend）。
   - 检查路由器是否开启了“AP 隔离”，这会导致 UDP 组播被阻断。

---

## 🛠 架构说明与自动化发版

本项目包含三个物理隔离的工程，互不干扰：

| 模块 | 目录 | 工具链 | 构建产物 |
|---|---|---|---|
| 服务端 | `/` (根目录) | AGP 4.2.2, Gradle 6.7.1, JDK 8 | `android-nas-server-*.apk` |
| 客户端 | `/client` | AGP 7.4.2, Gradle 7.5, JDK 11 | `android-nas-client-*.apk` |
| 桌面端 | `/desktop` | Python 3, PyInstaller | `nas-client-*.exe` / 可执行程序 |

**全自动发版机制**：GitHub Actions 会在编译前运行 `sync_version.py`。只要在 `CHANGELOG.md` 中修改了版本号，三端代码的 `versionCode`、`versionName` 以及最终生成的 APK/EXE 文件名都会自动同步更新，无需手动干预！

---

## 协议

本项目使用 [MIT License](LICENSE)。