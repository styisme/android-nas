# Changelog

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.3.0] - 2026-10-19

**安全特性增强：引入账号认证与加密开关**

### Added (Server)
- **Auth 认证系统**：新增 `AuthManager` 类。通过 HTTP Basic Auth 协议拦截网页端、JSON API 及 WebDAV 的未授权请求，默认账号密码为 `admin`。
- **安全控制面板**：`MainActivity` 新增“账号验证”与“HTTPS加密”两个 CheckBox 开关，修改后自动重启服务生效。
- **密码修改功能**：在 App 右上角菜单中可随时修改账号密码。
- **HTTPS 占位支持**：底层加入自签证书生成框架，为现代设备开启 HTTPS (8443端口) 提供可能。

### Added (Client)
- **认证适配**：局域网扫描机制升级，扫描到 `401 Unauthorized` 时也能识别出 NAS 设备。添加设备弹窗强制要求输入账号密码，并在请求时携带 `Authorization` 头。
- **HTTPS 兼容**：OkHttp 客户端全局配置了信任自签证书（Trust All Certs）逻辑，确保未来服务端开启 HTTPS 时客户端不会报错。

## [1.2.1] - 2026-10-19

**双端上传能力增强与错误排查优化**

### Added (Server)
- **拖拽与进度条**：网页端全新加入大区域文件拖拽，基于 `xhr.upload.onprogress` 实现真实百分比上传进度条显示。
- **HTTP PUT 方法支持**：允许第三方客户端以流式传输方式上传文件。
- **启动状态可视化**：由于端口冲突导致的 `BindException` 将直接显示在主界面的状态信息中。

### Added (Client)
- **客户端上传功能**：加入 FAB 上传按钮，调用系统文件选择器，并使用 OKHttp 通过 `PUT` 协议将文件流式上传。

### Fixed
- 修复了网页端在某些低分辨率浏览器下，悬浮按钮与卡片重叠的 UI 问题。

## [1.2.0] - 2026-10-19

**里程碑版本：双端架构成型与协议扩展**

### Added (Server)
- **WebDAV 网络邻居**：通过纯 Socket 原生实现 WebDAV 协议栈。完全支持 `PROPFIND` (目录)、`MKCOL` (新建)、`PUT` (上传)、`DELETE` (删除)。
- **Web 界面 (MD3)**：网页端全面重写，包含卡片阴影、动态色彩与深浅色适配。

### Added (Client)
- **MD3 客户端发布**：新增 `/client` 独立工程。使用原生 Java 编写。
- **局域网自动扫描**：利用 `WifiManager` 获取网关网段，通过线程池并发探测。
- **外部 NAS 兼容**：通过正则表达式解析 WebDAV `PROPFIND` 返回的 XML 树。
- **动态取色**：基于 `DynamicColors` 适配 Android 12+ 的壁纸取色。

## [1.0.0] - 2026-10-19

### Added
- 局域网网页管理：内置 NanoHTTPD。
- 基础文件操作：文件浏览、下载、表单上传。
- 本地管理 UI：服务状态、IP 显示、启动/停止、目录浏览。
- 开机自启与前台服务保活。
- 最低兼容 Android 2.2 (API 8)。