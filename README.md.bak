# Android NAS (Server & Client)

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com/)
[![Min SDK Server](https://img.shields.io/badge/Server_Min_SDK-API_8-blue.svg)]()
[![Min SDK Client](https://img.shields.io/badge/Client_Min_SDK-API_21-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

> 把任何一台闲置的 Android 手机变成局域网 NAS，并通过完美的 MD3 客户端与 WebDAV 网络邻居进行跨设备管理。

---

## ⚠️ AI 生成声明

> **本项目由 AI 辅助生成。**
> 
> 绝大多数代码（包括服务端架构、原生 WebDAV 协议栈、MD3 网页端、Android 客户端 UI 与网络逻辑）由大型语言模型（LLM）在开发者指导下生成，并经过人工多次真机测试、编译排错与修正。开发者负责需求定义、功能验收、多机型兼容性测试与最终发布决策。
> 
> 详细说明请见 [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md)。

---

## 🌟 为什么选择 Android NAS？

市面上的旧手机 NAS 应用大多不支持新系统，或者缺乏现代化的界面。本项目专为极客打造：
1. **服务端极限兼容**：纯 Framework API，最低支持 **Android 2.2 (API 8)**，让十年前的废置手机也能焕发第二春。
2. **客户端极致现代**：原生 Java 编写，采用 **Material Design 3**，支持 **动态取色 (Material You)**，完美融入你当下的主力机。
3. **无缝生态衔接**：不仅有自己的客户端，服务端还原生实现了 **WebDAV** 协议，Windows/macOS 可直接将其映射为本地网络驱动器。

---

## ✨ 详细功能矩阵

### 📱 服务端 (Server - 装在闲置手机)
- **双端口服务**：
  - `8080`：提供极速 Web 网页端管理（JSON API）。
  - `8081`：提供标准 WebDAV 网络邻居（PROPFIND 协议）。
- **网页端 (MD3 Web)**：多文件拖拽上传、在线编辑文本、新建/删除文件夹、图片在线预览。
- **保活机制**：前台服务 + `START_STICKY`，系统回收后自动拉起。
- **开机自启**：基于 `BOOT_COMPLETED` 静态广播，重启手机无需手动打开。
- **防越权保护**：严格的路径校验，防止恶意客户端通过 `../` 访问系统私密文件。

### 🖥️ 客户端 (Client - 装在主力手机)
- **多设备管理**：一级主页展示所有已保存设备，支持一键删除。
- **局域网秒搜**：下拉刷新，利用多线程并发扫描网段内的 `8080` 端口，自动发现 NAS。
- **双协议智能路由**：
  - 连接自带服务端时，走 JSON 接口。
  - 连接外部 NAS（如群晖）时，走 WebDAV 协议。
- **MD3 动态取色**：基于 Android 12+ 的 Monet 引擎，主题色随系统壁纸变化。
- **文件操作**：支持查看文件属性、下载文件到本地 `Download` 目录。

---

## 🚀 快速上手与使用指南

### 第一步：部署服务端
1. 在 GitHub Actions 下载 `android-nas-debug.apk`。
2. 安装到旧手机上，打开 App 并授予存储权限。
3. 点击 **“启动服务”**，界面会显示类似如下地址：
   - 网页端：`http://192.168.1.100:8080`
   - WebDAV：`http://192.168.1.100:8081`

> **⚠️ 注意**：Android 3.1+ 限制，安装后必须**手动打开一次 App**，否则重启手机无法触发开机自启。

### 第二步：电脑映射网络驱动器 (WebDAV)
**在 Windows 上**：
1. 打开“此电脑” -> 右键 -> **映射网络驱动器**。
2. 文件夹填入：`http://192.168.1.100:8081`。
3. 点击完成（若提示认证，直接留空或输入任意账号密码）。

**在 macOS 上**：
1. 访达 (Finder) -> 前往 -> **连接服务器**。
2. 输入：`http://192.168.1.100:8081`。

### 第三步：使用 Android 客户端
1. 安装 `nas-client-debug.apk`。
2. 打开 App，会自动扫描局域网内的 Android NAS。
3. 也可以点击右下角 `+` 号手动添加：
   - **添加安卓 NAS**：直接输入 IP 和 8080 端口。
   - **添加外部 NAS（如群晖）**：输入群晖的 WebDAV 地址（如 `http://192.168.1.50:5005`），并填入账号密码。

---

## ❓ 常见问题排查 (FAQ)

1. **为什么两台手机都在局域网，但客户端提示 `Failed to connect`？**
   - 如果是在**同一台手机**上同时跑服务端和客户端测试，请将地址改为 `127.0.0.1:8080`。部分安卓系统会拦截局域网 IP 自己连自己。
   - 检查服务端手机是否开启了系统防火墙或 AP 隔离。
2. **为什么客户端扫描不到服务端？**
   - 部分定制系统（如 MIUI、OriginOS）在后台扫描局域网时会限制并发，请确保客户端已获得“始终允许扫描局域网设备”的权限。
3. **WebDAV 挂载后没有写入权限？**
   - 服务端针对 `PUT` 和 `MKCOL` 做了支持，但部分 Windows 版本对 HTTP 明文 WebDAV 限制严格，建议使用客户端的下载/上传功能。

---

## 🛠 架构与构建

本项目包含两个完全物理隔离的工程：

| 模块 | 目录 | 工具链 | 最低兼容 |
|---|---|---|---|
| 服务端 | `/` (根目录) | AGP 4.2.2, Gradle 6.7.1, JDK 8 | Android 2.2 (API 8) |
| 客户端 | `/client` | AGP 7.4.2, Gradle 7.5, JDK 11 | Android 5.0 (API 21) |

**GitHub Actions** 已配置好，推送代码至 `main` 分支时，会自动触发 `Build Server APK` 与 `Build Client APK` 两条流水线。

---

## 协议

本项目使用 [MIT License](LICENSE)。