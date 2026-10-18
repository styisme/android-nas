# Changelog

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.2.0] - 2026-10-19

**里程碑版本：双端架构成型与协议扩展**

### Added (Server)
- **WebDAV 模块**：通过纯 Socket 原生实现 WebDAV 协议栈，支持 PROPFIND、MKCOL、PUT、DELETE 方法，开启 8081 端口。
- **Web 界面 (MD3)**：网页端全面重写，采用 Material Design 3 设计规范，加入悬浮按钮与卡片布局。
- **高级文件操作**：网页端支持多文件批量上传、在线编辑文本文件、图片直接预览、新建文件夹与递归删除。
- **关于页面**：注入网页端与 App 端的版本说明弹窗。
- **安全增强**：引入 `safeFile()` 方法防止 HTTP 路径穿越攻击。

### Added (Client)
- **MD3 客户端发布**：新增 `/client` 目录，基于 Java 的原生 Android 应用。
- **多设备管理**：一级界面展示设备列表，支持手动添加、卡片删除。
- **局域网自动扫描**：下拉刷新，多线程秒搜局域网内的 NAS 设备。
- **双协议路由**：针对 Android NAS 走 JSON 接口，针对外部 NAS 走 WebDAV 协议。
- **文件操作增强**：点击文件支持“下载”到系统 Download 目录，以及查看“文件属性”。
- **动态取色**：支持 Android 12+ 的 Material You 动态取色。
- **主题设置**：支持跟随系统、浅色模式、深色模式自由切换。

### Fixed
- 修复 NanoHTTPD 无法解析非标准 HTTP 方法导致崩溃的问题。
- 修复客户端 WebDAV 原生 XML 解析器崩溃，改用正则安全提取。
- 修复客户端添加设备弹窗被输入法遮挡的问题，全面升级为 MD3 `TextInputLayout`。
- 修复客户端 URL 拼接导致的双斜杠问题。

## [1.0.0] - 2026-10-19

### Added
- 局域网网页管理：内置 NanoHTTPD。
- 文件浏览、下载、上传。
- 本地管理 UI：服务状态、IP 显示、启动/停止、目录浏览。
- 开机自启与前台服务保活。
- 最低兼容 Android 2.2 (API 8)。