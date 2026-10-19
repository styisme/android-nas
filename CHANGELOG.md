# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [2.1.0] - 2026-10-19

**旗舰版本：客户端全面进阶与全平台生态闭环**

这是迄今为止最重要的一个里程碑版本。我们在保持服务端最低兼容 Android 2.2 的前提下，为客户端引入了现代化的多选与瀑布流体验，并将 Web 端与客户端的功能进行了完美的双向缝合。

### Added (Android Client)
- **网格瀑布流视图 (Grid View)**：文件列表现支持在“线性列表”与“三列网格”之间一键切换，大幅提升图片与视频的查阅体验。
- **多选与批量操作**：新增长按进入多选模式（Multi-Select）。顶部 Toolbar 会实时显示已选文件数量，并支持在菜单中一键批量删除选中的多个文件/文件夹。
- **文件重命名**：补齐了客户端缺失的重命名接口与 UI 弹窗。
- **新建文件夹**：客户端现支持在当前目录下创建新目录。
- **真实进度反馈**：上传与下载大文件时，引入 `LinearProgressIndicator` 弹窗，实时监听并渲染传输百分比进度。

### Added (Server & Web)
- **免密分享机制**：Web 端新增 `/api/share` 接口。点击文件的“分享”按钮，服务端会生成一个带 UUID Token 的短链接（`/share/:token`），外部用户点击后无需登录即可直接下载。
- **代码高亮预览**：网页端集成了 `highlight.js`，点击 `.json`、`.java`、`.html` 等代码文件时，支持多语言语法高亮展示。
- **全局递归搜索**：新增 `/api/search` 接口，Web 端顶部加入搜索框，支持在整个手机存储中匹配关键字。
- **访客权限隔离 (Guest)**：`AuthManager` 引入角色概念。默认提供 `guest / guest` 账号，该账号仅能访问和读取手机下的 `/Public` 目录。
- **运行日志面板**：服务端主页新增“运行日志”入口，实时记录谁在连接你的 NAS、访问了什么文件。

### Added (Desktop)
- **跨平台桌面客户端**：新增 `/desktop` 目录，使用 Python + Tkinter 开发的轻量级客户端。
- **多平台自动打包**：GitHub Actions 加入 PyInstaller 流水线，自动打包出 Windows 的 `nas-client.exe` 和 Linux 的可执行文件。

### Changed
- **Web UI 重构**：网页端彻底重构为现代 Material Design 3 风格，支持浅色/深色模式自动适配、卡片化布局与悬浮按钮。
- **接口数据增强**：`/api/list` 与 `/api/search` 返回的 JSON 中新增了 `isTxt`、`isImg` 标识，便于客户端直接调用系统组件预览媒体文件。

### Fixed
- **大文件上传卡死 (致命修复)**：修复了 OkHttp 原生上传时未显式提供 `Content-Length` 请求头，导致老版本 NanoHTTPD 2.2.0 无限阻塞等待的问题。
- **WebDAV 兼容性**：摒弃了容易崩溃的 `XmlPullParser`，改用正则表达式解析 WebDAV 返回的 XML 响应，彻底修复了对接群晖等外部 NAS 时的 `precondition: START_TAG` 崩溃。
- **UI 遮挡问题**：修复了添加设备弹窗在软键盘弹出时被挤压截断的问题，全面替换为 MD3 `TextInputLayout` 并加入 `ScrollView` 包裹。
- **编译工具链冲突**：解决了 GitHub Actions 中 JDK 17 与旧版 Gradle 6.7.1 不兼容导致构建失败的问题，锁定了 JDK 8 环境。

---

## [1.3.0] - 2026-10-19

**安全特性增强：引入账号认证与加密开关**

### Added
- **Auth 认证系统**：新增 `AuthManager` 类。通过 HTTP Basic Auth 协议拦截网页端、JSON API 及 WebDAV 的未授权请求。
- **安全控制面板**：`MainActivity` 新增“账号验证”与“HTTPS加密”两个 CheckBox 开关，修改后自动重启服务生效。
- **密码修改功能**：在 App 右上角菜单中可随时修改管理员账号密码。
- **HTTPS 占位支持**：底层加入自签证书生成框架，为现代设备开启 HTTPS (8443端口) 提供可能。
- **客户端认证适配**：局域网扫描机制升级，扫描到 `401 Unauthorized` 时也能识别出 NAS 设备。请求时自动携带 `Authorization` 头。
- **自签证书信任**：OkHttp 客户端全局配置了 `TrustManager`，信任所有自签证书。

### Changed
- **后台保活优化**：加入 CPU 唤醒锁 (`WakeLock`) 和 Wi-Fi 高性能锁，防止手机息屏后系统切断网络。
- **电池优化引导**：首次启动服务时，弹窗引导用户忽略电池优化，提升后台存活率。

---

## [1.2.1] - 2026-10-19

**双端上传能力增强与错误排查优化**

### Added
- **拖拽与进度条**：网页端全新加入大区域文件拖拽（Drag & Drop），基于 `xhr.upload.onprogress` 实现真实百分比上传进度条显示。
- **HTTP PUT 支持**：`NasHttpServer` 新增 `PUT` 方法处理，允许第三方客户端以流式传输方式上传文件。
- **启动状态可视化**：若端口被占用导致 `BindException`，服务端首页会直接以红字显示错误原因，不再静默失败。
- **启动器兼容**：修复 Gradle Wrapper 版本被污染问题，确保客户端与服务端的构建完全物理隔离。

---

## [1.2.0] - 2026-10-19

**里程碑版本：双端架构成型与协议扩展**

### Added
- **WebDAV 网络邻居**：通过纯 Socket 原生实现 WebDAV 协议栈（开启 8081 端口）。支持 `PROPFIND`、`MKCOL`、`PUT`、`DELETE`。
- **MD3 客户端发布**：新增 `/client` 目录，纯 Java 编写的原生 Android 应用。全面采用 Material Design 3 规范，支持动态取色。
- **局域网自动扫描**：利用 `WifiManager` 获取网关网段，通过 `ExecutorService` 开启 20 线程并发探测。
- **外部 NAS 路由**：客户端支持添加外部 NAS（群晖、威联通），通过正则表达式解析 WebDAV 返回树。
- **高级文件操作**：网页端新增多文件上传、在线编辑文本、新建/递归删除文件夹。

---

## [1.0.0] - 2026-10-19

**首个正式版本：从零到一的突破**

### Added
- **局域网网页管理**：内置 NanoHTTPD 服务器（8080 端口），提供基础的 Web UI 文件浏览、表单上传与下载。
- **本地管理 UI**：App 端提供文件列表浏览、服务启停控制、IP 地址显示。
- **保活机制**：基于 `START_STICKY` 和 `FOREGROUND_SERVICE`，配合 `BOOT_COMPLETED` 开机自启广播。
- **极限兼容**：锁定 `minSdkVersion 8` (Android 2.2)，通过反射调用修复已废弃的 `setLatestEventInfo` 方法。
- **全套构建脚本**：支持 GitHub Actions 云端一键编译。