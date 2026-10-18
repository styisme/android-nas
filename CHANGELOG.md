# Changelog

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.2.1] - 2026-10-19

**双端上传能力增强与错误排查优化**

### Added (Server)
- **拖拽与进度条**：网页端全新加入大区域文件拖拽（Drag & Drop），基于 `xhr.upload.onprogress` 实现真实百分比上传进度条显示。
- **HTTP PUT 方法支持**：`NasHttpServer` 新增 `PUT` 方法处理。允许第三方客户端（如安卓客户端、第三方文件管理器）以流式传输方式上传文件，极大降低了服务端处理大文件时的内存占用。
- **启动状态可视化**：`HttpServerService` 引入 `lastError` 变量，由于端口冲突导致的 `BindException` 将直接显示在主界面的状态信息中。

### Added (Client)
- **客户端上传功能**：`FileListActivity` 加入浮动操作按钮 (FAB)，调用系统文件选择器 (`ACTION_GET_CONTENT`)，并使用 OKHttp 通过 `PUT` 协议将文件流式上传至当前目录。
- **内存优化**：客户端上传与下载均采用 `8192` 字节缓冲区进行流式读写，杜绝大文件导致的 OOM（内存溢出）。

### Fixed
- 修复了客户端使用 WebDAV 协议下载文件时，因为 `Content-Length` 缺失导致的下载挂起问题。
- 修复了网页端在某些低分辨率浏览器下，悬浮按钮与卡片重叠的 UI 问题。

## [1.2.0] - 2026-10-19

**里程碑版本：双端架构成型与协议扩展**

### Added (Server)
- **WebDAV 网络邻居**：通过纯 Socket 原生实现 WebDAV 协议栈。完全支持 `PROPFIND` (目录)、`MKCOL` (新建)、`PUT` (上传)、`DELETE` (删除) 等核心请求。
- **Web 界面 (MD3)**：网页端全面重写，采用 Material Design 3 设计规范，包含卡片阴影、动态色彩与深浅色适配。
- **关于页面**：网页端与 App 端同步加入版本说明。

### Added (Client)
- **MD3 客户端发布**：新增 `/client` 独立工程。使用原生 Java 编写，界面全面 Material 3 化。
- **局域网自动扫描**：利用 `WifiManager` 获取网关网段，通过 `ExecutorService` 开启 20 线程并发探测 `8080` 端口。
- **外部 NAS 兼容**：通过正则表达式解析 WebDAV `PROPFIND` 返回的 XML 树，兼容群晖等外部设备。
- **动态取色**：基于 `DynamicColors.applyToActivitiesIfAvailable()` 适配 Android 12+ 的壁纸取色。

## [1.0.0] - 2026-10-19

### Added
- 局域网网页管理：内置 NanoHTTPD，完美适配极低版本系统。
- 基础文件操作：文件浏览、下载、表单上传。
- 本地管理 UI：服务状态、IP 显示、启动/停止、目录浏览。
- 开机自启与前台服务保活。
- 最低兼容 Android 2.2 (API 8)。