# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [3.2.0] - 2026-10-19

**史诗级生态闭环：五端协同与全协议制霸**

这是一个极其重磅的里程碑版本。我们在保持底层极低兼容性的前提下，成功打通了手机、电视、手表、电脑与网页的壁垒，并实现了几乎所有主流局域网协议的支持。

### Added (Server & Web)
- **原生 FTP 服务端**：手写底层 Socket 实现 FTP 协议栈，开启 `2121` 端口。支持 PASV 被动模式，完美兼容 Windows 资源管理器与老旧电视盒子。
- **原生 DLNA/UPnP 媒体服务器**：手写 SSDP 组播发现与 SOAP 请求解析，无需在电视安装 App，即可让电视直接发现 NAS 并播放视频。
- **极寒省电模式 (Aggressive Low Power)**：专为挂机旧手机设计。开启后，系统会自动强制降级 Wi-Fi 锁频率，直接切断高耗电的内网穿透长连接，并关闭 LocalSend 和 DLNA 广播，仅保留核心 Web、WebDAV 与 FTP，极限延长电池寿命。
- **Web 端流媒体与工具**：新增 MP4/MP3 视频音频在线播放（支持 Range 分片请求与进度条拖动）、ZIP 文件在线解压、局域网剪贴板同步。
- **服务端驾驶舱**：在 App 本地新增极客控制面板，支持自定义别名、一键开关所有服务、修改各类端口、修改管理员密码。

### Added (Client)
- **多协议智能路由**：手机客户端现已支持原生 JSON、WebDAV、FTP 三种协议的无缝切换。在添加设备时，可自由选择目标协议。
- **跨端协同（配置下发）**：手机端新增“发送配置到电视”功能，通过底层 UDP 广播自动发现局域网内的 TV 端，并瞬间将 NAS 的账号密码下发给电视。
- **跨端协同（一键投屏）**：在手机文件列表中，长按或点击视频，新增“投屏到电视”选项。手机会直接发送 Play 指令与视频流地址给 TV 端。

### Added (TV & Watch)
- **全新 TV 电视端**：专为老旧电视盒子打造，最低兼容 Android 4.4 (API 19)。纯原生编写，无任何第三方库（包体极小）。完美适配遥控器焦点操作，内置哔哩哔哩同款“全屏虚拟键盘 + 边打边搜”功能。支持接收手机投屏与配置下发。
- **全新 Watch 手表端**：最低兼容 Android 5.0 (API 21)。UI 引入 MD3 卡片风格，动态判断圆盘/方盘自动调整防遮挡内边距。支持多设备管理与多协议（原生/WebDAV/FTP）密码输入。支持腕上遥控投屏。

### CI/CD & DevOps
- **全自动化版本号同步**：重写 `sync_version.py`。现在推送到 `main` 分支时，GitHub Actions 会自动读取 `CHANGELOG.md` 中的最新版本号，动态修改服务端、手机端、TV端、手表端与桌面端的 `versionCode` 与 `versionName`，并自动重命名最终生成的 APK/EXE 产物，彻底解决“找不到最新包”或“包名冲突”的问题。

### Fixed
- **内存溢出与闪退修复**：
  - 修复 Android 10+ 下载文件时，尝试写入公共 `Download` 目录因分区存储导致的崩溃。现已安全下沉至 App 专属沙盒目录。
  - 修复服务端在 `getResponseCode()` 时因网络异常导致的 `IOException` 未捕获崩溃。
- **并发与线程安全**：
  - 修复 `LogManager` 在多并发访问时的 `ConcurrentModificationException` 崩溃，加入 `Collections.synchronizedList` 同步锁。
  - 修复客户端在快速切换“网格/列表”视图时的内存泄漏与多选状态错乱问题。
- **UI 适配修复**：
  - 修复手表端添加设备弹窗因小屏幕导致“账号密码”被遮挡的问题（加入 `ScrollView`）。
  - 修复手表端及 TV 端在 `AndroidManifest.xml` 中遗漏 `FileListActivity` 注册导致的 `ActivityNotFoundException` 闪退。
- **权限适配**：完美适配 Android 13+ 的 `READ_MEDIA_IMAGES` 相册权限与 `POST_NOTIFICATIONS` 通知权限。

---

## [3.0.0] - 2026-10-19

**全能媒体中心：从文件服务器向私有云的蜕变**

### Added (Server & Web)
- **Web 端流媒体播放**：新增视频/音频在线播放能力，支持 Range 分片，拖动进度条秒加载。
- **在线解压**：网页端新增 ZIP 在线解压功能，无需下载到本地即可完成解压。
- **局域网剪贴板**：网页端与 App 端新增剪贴板实时同步功能，跨设备复制粘贴文本。
- **免密分享短链**：新增带 Token 的免密分享机制，外部人员访问分享链接无需登录即可下载。
- **全局递归搜索**：新增全盘文件搜索接口，支持 Web 端与 App 端实时检索。

### Added (Desktop)
- **Python 跨平台桌面端**：新增 `desktop` 工程。基于 Tkinter 原生开发，支持 Windows 与 Linux，一键自动打包 `.exe`。

### Fixed
- **大文件上传卡死**：彻底修复 OkHttp 原生上传时未显式提供 `Content-Length`，导致底层 NanoHTTPD 阻塞卡死的致命问题。
- **WebDAV 兼容性**：摒弃了容易在部分服务器上崩溃的 `XmlPullParser`，改用正则表达式重构 WebDAV XML 响应解析。
- **UI 遮挡问题**：修复了客户端添加设备弹窗在软键盘弹出时被截断的问题，全面替换为 MD3 `TextInputLayout` 并加入 `ScrollView`。

---

## [2.6.0] - 2026-10-19

**生态爆发：拥抱 LocalSend 与全自动发版**

### Added
- **LocalSend 协议原生接收**：服务端开启 `53317` 端口，内置 UDP 组播发现与 HTTP 接收引擎。官方 LocalSend 客户端可直接向 NAS 投递文件。
  - 支持 PIN 验证（默认 1234）与防爆破机制。
  - 同名文件接收自动重命名为 `(1)`, `(2)`，防覆盖。
  - 引入 Session 定时清理，超过 5 分钟未活动的死连接自动销毁，防止内存泄漏。
- **内网穿透中心**：
  - 一键免注册穿透：自动遍历测试 `Tunnl.gg`、`Skytunnel`、`Serveo` 等 7 大免费节点，连通即生成 HTTPS 公网链接。
  - 高级 SSH 穿透：支持填入自定义公网服务器 IP 与账号密码，实现稳定加密映射。
- **客户端设置中心重构**：提供深浅色主题切换、自定义局域网扫描超时时间、自动扫描开关。

### CI/CD & DevOps
- **初始引入 `sync_version.py`**：实现基于 `CHANGELOG.md` 的版本号自动注入。

---

## [2.1.0] - 2026-10-19

**旗舰里程碑：客户端全面进阶与安全体系**

### Added
- **安全控制面板**：App 首页加入“账号验证”与“HTTPS加密”开关。
- **Auth 认证系统**：引入 `AuthManager`，全面拦截网页端、JSON API 及 WebDAV 的未授权请求。新增 `guest/guest` 访客账号（仅限访问 `/Public` 目录）。
- **网格瀑布流视图**：手机客户端文件列表支持在“线性列表”与“三列网格”之间一键切换。
- **多选与批量操作**：新增长按进入多选模式，支持一键批量删除。
- **真实进度反馈**：上传与下载大文件时，使用 `LinearProgressIndicator` 实时显示百分比。
- **二维码秒连**：服务端生成连接二维码，客户端支持直接扫码添加。

---

## [1.4.0] - 2026-10-19

**安全防护与全平台铺路**

### Added
- **后台保活升级**：加入 `PowerManager.WakeLock` (CPU锁) 和 `WifiManager.WifiLock` (Wi-Fi高性能锁)，彻底解决旧手机息屏后网卡休眠导致断连的痛点。
- **HTTPS 框架支持**：底层加入自签证书生成框架（8443端口），客户端 OkHttp 配置信任所有自签证书。

---

## [1.3.0] - 2026-10-19

**双端上传能力增强与错误排查优化**

### Added
- **网页端拖拽上传**：加入大区域文件拖拽（Drag & Drop），基于 `xhr.upload.onprogress` 实现真实百分比进度条。
- **HTTP PUT 协议支持**：`NasHttpServer` 新增 `PUT` 方法处理，允许客户端以纯二进制流式传输方式上传文件。
- **启动状态可视化**：若端口被占用导致 `BindException`，服务端首页会直接以红字显示错误原因，不再静默失败。
- **客户端原生上传**：Android 客户端新增悬浮上传按钮（FAB），接入 `PUT` 协议。

---

## [1.2.0] - 2026-10-19

**双端架构成型与协议扩展**

### Added
- **WebDAV 网络邻居**：通过纯 Socket 原生实现 WebDAV 协议栈（开启 8081 端口）。支持 `PROPFIND`、`MKCOL`、`PUT`、`DELETE`。
- **MD3 客户端发布**：新增 `/client` 目录。纯 Java 编写的原生 Android 应用，全面采用 Material Design 3 规范，支持动态取色。
- **局域网自动扫描**：客户端利用 `WifiManager` 获取网关网段，通过 `ExecutorService` 开启多线程并发探测 8080 端口。

---

## [1.0.0] - 2026-10-19

**从零到一的突破：首个正式版本**

### Added
- **局域网网页管理**：内置 NanoHTTPD 服务器（8080 端口），提供基础的 Web UI 文件浏览、表单上传与下载。
- **本地管理 UI**：App 端提供文件列表浏览、服务启停控制、IP 地址显示。
- **保活机制**：基于 `START_STICKY` 和 `FOREGROUND_SERVICE`，配合 `BOOT_COMPLETED` 开机自启广播。
- **极限兼容**：锁定 `minSdkVersion 8` (Android 2.2)，通过反射调用修复已废弃的 `setLatestEventInfo` 方法。
- **全套构建脚本**：支持 GitHub Actions 云端一键编译。