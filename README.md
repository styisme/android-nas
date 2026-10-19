# Android NAS (Server & Client & Desktop)

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com/)
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> 把任何一台闲置的 Android 手机变成局域网私有云 NAS，并通过现代化的 MD3 客户端、WebDAV 网络邻居及跨平台桌面端进行无缝管理。

---

## ⚠️ 关于 AI 生成声明

> **本项目由 AI 辅助生成。**
> 
> 绝大多数代码（包括服务端架构、原生 WebDAV 协议栈、MD3 网页端、Android 客户端 UI 与网络逻辑、Python 桌面端及自动化构建脚本）由大型语言模型（LLM）在开发者指导下生成，并经过人工多次真机测试、编译排错与修正。开发者负责需求定义、功能验收、多机型兼容性测试与最终发布决策。
> 
> 详细说明请见 [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md)。

---

## 🌟 为什么选择本项目？

市面上大部分旧手机 NAS 应用要么不支持现代系统，要么没有安全防护。本项目专为极客打造，具备三大核心优势：
1. **极限兼容与现代体验并存**：服务端最低支持 **Android 2.2 (API 8)**，让十年前的废置手机焕发第二春；而客户端与 Web 端则采用当下最新的 **Material Design 3 (MD3)** 设计规范。
2. **全平台生态覆盖**：服务端原生实现了 **WebDAV** 协议，不仅自带的 Android 客户端可以无缝访问，Windows/macOS 也能直接将其映射为本地网络驱动器，甚至还提供了独立打包的桌面端。
3. **安全与自由度**：内置严格的路径防穿越校验、完整的账号密码权限体系（区分管理员与访客）、文本在线代码高亮、以及一键免密分享功能。

---

## ✨ 详细功能矩阵

### 📱 服务端 (Server - 装在闲置手机)
- **双端口并发服务**：
  - `8080` 端口：提供极速 Web 网页端管理与 JSON API。
  - `8081` 端口：提供标准 WebDAV 网络邻居（PROPFIND 协议）。
- **驻留与保活能力**：
  - 前台服务常驻通知 + `START_STICKY` 重建机制。
  - CPU 唤醒锁 (WakeLock) + Wi-Fi 高性能锁，防止息屏断网。
  - 基于 `BOOT_COMPLETED` 的开机自启。
- **可视化排错**：若端口被占用或启动失败，App 首页会直接以红字显示错误原因。

### 🌐 Web 网页端 (MD3 Web)
- 全区域文件拖拽上传（Drag & Drop），带真实的百分比进度条。
- 内置 highlight.js，文本文件在线预览支持多语言代码高亮。
- 一键生成带随机 Token 的**免密分享链接**。
- 全局文件递归搜索。
- 支持多文件并发上传、新建/递归删除文件夹、在线编辑保存。

### 🖥️ Android 客户端 (Client - 装在主力手机)
- **多协议智能路由**：连接自带服务端时走原生 JSON 接口；连接外部 NAS（如群晖、威联通）时走 WebDAV 协议。
- **高效文件管理**：
  - **网格瀑布流**：一键切换多列网格视图浏览图片。
  - **长按多选**：支持勾选多个文件，一键批量删除。
  - **管理能力**：支持重命名、新建文件夹。
  - **真实进度**：文件上传与下载均有 MD3 进度条反馈。
- **自动化发现**：利用线程池并发扫描局域网 `8080` 端口，支持扫描服务端生成的二维码直接连接。

### 💻 桌面端 (Desktop - Win/Linux)
- 基于 Python + Tkinter 原生开发，内存占用极低。
- 支持文件浏览、上传、下载、新建文件夹、删除等全套操作。
- 已配置 GitHub Actions，自动打包 Windows 的 `.exe` 和 Linux 可执行文件，双击即用。

---

## 🚀 快速上手与使用指南

### 第一步：部署服务端
1. 在 GitHub Actions 下载 `android-nas-debug.apk`。
2. 安装到旧手机上，打开 App 并授予存储权限。
3. 点击 **“启动服务”**。首页会显示：
   - 网页端：`http://192.168.1.100:8080`
   - WebDAV：`http://192.168.1.100:8081`
   - 默认管理员：`admin / admin` ；默认访客：`guest / guest`（仅限 `/Public` 目录）。

> **⚠️ 注意**：受 Android 3.1+ 限制，安装后必须**手动打开一次 App**，否则重启手机无法触发开机自启。

### 第二步：使用 Web 网页端
在电脑或手机浏览器打开 `http://手机IP:8080`：
- 登录后，你可以直接把电脑上的文件**拖拽**到网页里上传。
- 点击代码文件可进行高亮预览或直接修改。
- 点击“分享”按钮，即可生成免密码的下载链接发给别人。

### 第三步：挂载为网络驱动器 (WebDAV)
**在 Windows 上**：
1. 打开“此电脑” -> 右键 -> **映射网络驱动器**。
2. 文件夹填入：`http://192.168.1.100:8081`。
3. 输入账号密码（admin / admin）。

**在 macOS 上**：
1. 访达 -> 前往 -> **连接服务器**。
2. 输入：`http://192.168.1.100:8081`。

### 第四步：使用 Android 客户端
1. 下载 `nas-client-debug.apk` 并安装。
2. App 会自动扫描局域网内的设备；也可以点击右下角 `+` 号，选择**扫描服务端的二维码**或手动输入 IP 添加。
3. 进入文件管理后，**长按**任意文件可进入多选模式；点击右上角菜单可以切换**网格视图**。

---

## 📡 服务端 API 接口文档 (8080端口)

*注：如果开启了“账号验证”开关，所有请求必须携带 `Authorization: Basic Base64(账号:密码)` 请求头。*

| 接口 | 方法 | 参数 | 说明 |
|---|---|---|---|
| `/api/list` | `GET` | `path` (当前路径) | 获取目录下的文件列表（返回 JSON） |
| `/api/search` | `GET` | `q` (关键字) | 全局递归搜索文件 |
| `/api/download` | `GET` | `path` (文件路径) | 下载文件（支持流式传输） |
| `/api/preview` | `GET` | `path` (文件路径) | 获取文本文件内容（用于高亮/编辑） |
| `/api/upload` | `POST` | `path`, `file` | 网页端表单多文件上传 |
| `/api/mkdir` | `POST` | `path`, `name` | 新建文件夹 |
| `/api/delete` | `POST` | `path` | 递归删除文件或文件夹 |
| `/api/rename` | `POST` | `path`, `newName` | 重命名文件 |
| `/api/save` | `POST` | `path`, `content` | 保存在线编辑的文本内容 |
| `/api/share` | `POST` | `path` | 生成免密分享 Token |
| `/share/:token`| `GET` | - | 通过 Token 免密下载文件 |
| `/*` (任意路径) | `PUT` | 文件流 | 客户端 WebDAV 标准流式上传 |

---

## ❓ 常见问题排查 (FAQ)

1. **浏览器或客户端提示 `ERR_CONNECTION_REFUSED` (拒绝连接)？**
   - 查看服务端 App 首页是否显示了**红色错误提示**。若提示端口被占用，请重启手机或关闭占用 8080/8081 的其他软件。
   - 如果是在**同一台手机**上同时跑服务端和客户端测试，请将地址改为 `127.0.0.1:8080`。
   - 确保两台设备连在同一个 WiFi，且路由器未开启“AP隔离”。
2. **为什么手机息屏后，电脑就传不动文件了？**
   - 请进入手机系统设置，找到本应用，将电池策略设为**“无限制”**或**“允许后台高耗电”**。App 已经申请了锁，但部分定制 ROM（如 MIUI、HarmonyOS）需手动放行。
3. **客户端无法上传大文件？**
   - 本项目已通过 `Content-Length` 强制标定修复了 NanoHTTPD 的卡死问题。若依然失败，请检查服务端内存是否充足。

---

## 🛠 架构说明与源码结构

本项目包含三个完全物理隔离的工程，互不干扰：

| 模块 | 目录 | 工具链 | 最低兼容 | 构建产物 |
|---|---|---|---|---|
| 服务端 | `/` (根目录) | AGP 4.2.2, Gradle 6.7.1, JDK 8 | Android 2.2 (API 8) | `android-nas-debug.apk` |
| 客户端 | `/client` | AGP 7.4.2, Gradle 7.5, JDK 11 | Android 5.0 (API 21) | `nas-client-debug.apk` |
| 桌面端 | `/desktop` | Python 3, PyInstaller | Win/Linux 全平台 | `nas-client.exe` / 可执行程序 |

**GitHub Actions** 配置在 `.github/workflows/` 下，推送至 `main` 分支即触发三端自动构建。

---

## 协议

本项目使用 [MIT License](LICENSE)。