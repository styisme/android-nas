# Android NAS 底层架构深度解析 (v4.4.0)

本项目之所以能在极低兼容性（API 8）下实现极高的性能，并且横跨十个完全不同的平台与操作系统，源于底层大量抛弃了第三方重型框架，采用了**纯手写 Socket、原生硬件调用、SQLite 数据库引擎与极简设计**。

本文档将深度剖析本项目的核心架构设计，带你了解代码底层的运行原理。

---

## 一、 核心容器调度与拦截器 (HttpServerService)

服务端以 `Foreground Service`（前台服务）的形式常驻内存，在内部开启了多个独立的线程池并发监听不同端口，互不干扰：
- **8080 (HTTP / HTTPS)**：基于 NanoHTTPD 二次封装。在路由分发前，底层加入了**多用户权限拦截器**、`SecurityFilter`（防爆破与 IP 白名单）以及**路径沙箱映射器**。处理前端鉴权、流媒体请求、PWA 静态资源分发（`manifest.json`, `sw.js`）与数据操作。支持在运行时动态生成自签证书以启用 HTTPS。
- **8081 (WebDAV)**：**纯 Socket 手写实现**。直接解析 HTTP 报文，处理 `PROPFIND` 的 XML 拼装、`PUT` 的流式二进制写入，并在底层强制多用户根目录隔离。
- **2121 (FTP)**：**纯 Socket 手写实现**。处理 FTP 控制连接，支持 `PASV` 被动模式，管理数据通道。
- **53317 (LocalSend)**：实现 UDP 组播发现协议，并接管 HTTP 的文件流式写入。
- **8090 (UDP 协同)**：监听广播报文，用于 TV 端与手机端的局域网发现与配置下发。
- **8082 (WebSocket 远控)**：独立承载高并发的实时视频流下发与低延迟的触控指令上报，底层强制 Token 鉴权与权限校验。

---

## 二、 并发隔离与多用户沙箱架构

系统的核心在于 `DatabaseManager` 与 `AuthManager`，它们让 NAS 从“单机玩具”蜕变为“家庭私有云”。

### 1. 数据库设计与加盐哈希
服务端首次启动时，会自动在应用沙盒内生成 `nas_core.db`，包含两张核心表：
- `users`：存储用户名、密码、角色（Admin/Guest/Normal）、专属的 `root_dir`（物理根目录）以及**紧急安全码（PIN）**。自 v4.4.0 起，所有密码均采用 **SHA-256 + 随机盐（Salt）** 加密存储，彻底杜绝明文泄露风险。
- `file_hashes`：记录文件 MD5 与路径的映射，用于实现 TB 级大文件的“秒传”比对。

### 2. 高并发身份隔离 (ThreadLocal)
为避免多线程池下的请求身份串号（越权），本项目彻底废弃了全局静态用户变量。`AuthManager` 引入了 `ThreadLocal`。在处理 HTTP、WebDAV、FTP 请求的线程结束时，**必须**调用 `clearCurrentUser()` 清理状态，防止下一个复用该线程的请求意外继承前一个用户的管理员权限。

### 3. 动态沙箱映射 (Dynamic Path Mapping)
所有的文件操作不再直接读写 `/sdcard`。
- 当请求到达时，`AuthManager` 解析 Basic Auth，获取当前用户名。
- `NasHttpServer.safeFile()` 拦截器会将用户请求的 `/` 强制映射到其专属的 `root_dir`（如 `/Users/user1`），并使用 `getCanonicalPath` 校验，彻底杜绝 `../` 越权穿越。
- 仅 `admin` 账号在开启全盘模式后，可绕过此映射，直达 `/data` 和 `/system`。

---

## 三、 多媒体与文件处理引擎

为了告别枯燥的文件列表，底层新增并优化了多项重型处理引擎。

### 1. 影视元数据刮削引擎 (MediaScraper)
- **极简原理**：为了避免引入庞大的视频解析库，底层采用纯 Java 正则表达式（Regex）对文件名进行切片（如 `Inception.2010.1080p.mp4`），精准提取片名与年份。
- **网络请求**：利用 `HttpURLConnection` 请求免 Key 的公开聚合 API，提取海报 URL、评分与简介，实现零配置的海报墙展示。

### 2. 大文件秒传与分片断点续传
- **秒传机制**：前端计算文件 MD5 后请求 `/api/upload/check`。服务端通过查询 SQLite 的 `file_hashes` 表（强制按用户隔离），若已存在则直接返回 `SECONDS_UPLOAD`，实现 0 秒极速上传。
- **分片合并**：前端将大文件切为 2MB 分片。服务端通过 `FileOutputStream(file, true)` 将分片流式追加至 `.part` 临时文件。临时文件名包含 Hash 以防止并发覆盖。
- **线程安全**：在最后一片到达触发合并重命名时，使用 `synchronized` 加锁，防止并发写入导致文件损坏。

### 3. 影视缩略图与流式打包
- **缩略图引擎**：利用 `BitmapFactory.Options.inSampleSize` 降采样，将原图压缩为 300px 的 JPEG，并静默缓存至原目录的 `.thumbnails` 隐藏文件夹。
- **流式打包引擎**：底层利用 `ZipOutputStream`，将多选文件分块读取并直接写入 HTTP 响应流，实现“边压边传”，杜绝本地临时空间占用。

### 4. Webhook 事件通知中枢 (WebhookManager)
- **轻量化实现**：放弃第三方 HTTP 库，使用 `HttpURLConnection` 异步发送 JSON 格式的事件通知。
- **协议适配**：底层内置对 Telegram (Form 表单) 与企业微信 (JSON 文本) 的格式适配，在上传完成、异常触发时实时推送到用户终端。

---

## 四、 多模态远程控制引擎 (Remote Desktop Engine)

这是项目中最硬核的底层模块，实现了一套无需外网、纯局域网内的超低延迟屏幕控制方案。

### 1. 画面捕获与编码 (H264 & Root)
- **H264 硬件推流**：利用 `MediaProjection` 获取屏幕，通过 `VirtualDisplay` 投射给 `MediaCodec`。使用 `COLOR_FormatSurface` 进行硬件级 H264 编码，生成裸流。
- **Root 截图降级**：如果未授权录屏，系统会自动降级使用 Root 权限循环执行 `screencap -p`，转换为 JPEG 格式通过 WebSocket 推流。

### 2. 前端解码与双引擎触控
- **流式传输**：网页端引入 `JMuxer.js`，接收 WebSocket 推来的 H264 裸流，实时封装为 `fMP4` 喂给 `<video>` 标签。
- **双引擎注入**：网页端鼠标事件转为 `tap,x,y` 字符串。服务端收到后，优先调用 `AccessibilityService` 模拟手势；若无障碍未开启，则降级调用 `su -c input tap` 注入。
- **安全注入**：坐标参数强制转换为 `Float` 并限制范围，彻底杜绝拼接 `su` 命令导致的 Shell 注入风险。

### 3. WebSocket 严格鉴权
- 远控 8082 端口不再接受匿名连接。前端必须通过 `ws://ip:8082/?token=<Base64>` 连接，服务端严格解码校验，且仅限 Admin 角色接入。

---

## 五、 企业级安全底座与极端机制 (v4.4.0 新增)

- **动态自签证书 (CertificateUtils)**：服务端启动时，在内存中动态生成自签证书以支持 HTTPS/WSS。若在极老旧的设备上（API 8）加密库受限，则会平滑降级回 HTTP。
- **防爆破与 IP 白名单 (SecurityFilter)**：全局拦截器会记录失败的鉴权请求。连续失败 5 次自动封禁 IP 15 分钟。若开启 IP 白名单，非授权 IP 直接 403 拦截。
- **安全审计持久化**：记录异常登录、文件删除、高危 Shell 执行等操作，写入 `AndroidNAS/audit.log`。
- **☢️ 终极自毁模式 (Panic Wipe)**：提供 `/api/system/wipe` 核弹级接口。前端经过多重密码及系统指纹验证后触发。服务端开启子线程递归销毁全盘文件，若包含 Root 参数，还会执行底层格式化并重启手机，实现物理级数据销毁。

---

## 六、 极限兼容与沙盒突破策略

- **API 8 的妥协**：为了能在十年前的设备上跑，服务端 `minSdkVersion` 锁死在 8。代码中全面废弃了 Lambda 表达式，改用传统匿名内部类（避免 AGP 4.2.2 编译报错）。
- **高版本适配守卫**：针对 Android 11+ 强制接入 `MANAGE_EXTERNAL_STORAGE`；针对 Android 13+ 引入相册媒体权限；针对电池读取 API 引入 21 版本守卫。
- **全盘突破 (Root 模式)**：在开启 Root 模式下，Java 层的 `File` 操作不再受系统沙盒校验，直接实现底层的全盘掌控。

---

## 七、 极客级保活矩阵 (Keep-Alive Matrix)

为了对付国产 ROM 严苛的杀后台机制，我们采取了四层立体防御：
1. **前台服务与锁**：`START_STICKY` + 通知栏常驻，辅以 `WakeLock` 和 `WifiLock`。
2. **无声音频欺骗**：启动 `SilentPlayer` 循环写入 0 字节 PCM，欺骗系统判定为活跃音乐播放器。
3. **定时心跳拉活**：利用 `AlarmManager` 注册心跳闹钟。
4. **Root 免死**：直接向 `/proc/<pid>/oom_score_adj` 写入 `-1000`，并冻结厂商底层杀后台服务。

---

## 八、 跨端协同协议 (UDP Discovery)

设备之间通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 广播。接收方（TV/手表）回复自身 IP。发送方拿到 IP 后，通过 HTTP POST 将密码与视频流地址直接推送到目标设备，实现秒级联动。

---

## 九、 自动化构建与全量发版矩阵 (CI/CD)

`sync_version.py` 是整个 DevOps 流程的核心枢纽。它通过正则解析 `CHANGELOG.md`，自动将提取的版本号（如 4.4.0）换算为 `versionCode`（如 40400），并动态注入到 10 个不同端的工程配置中。

### 1. 双轨制构建与签名机制
- **日常调试**：只触发对应端的独立编译（`assembleDebug`），包名自动追加 `.debug` 后缀，防止覆盖正式版。
- **全量发版**：修改 `CHANGELOG.md` 触发矩阵（`assembleRelease`）。通过 GitHub Secrets 注入 `release.keystore` 完成正式签名。

### 2. 高容错与平铺直传
- 脚本使用 `find -type f` 和 `|| true` 提取产物，即使某端（如手表）编译失败，也不影响其他 9 个端发布。
- 所有产物按 `Name-vVersion` 规范重命名，直接平铺展示在 GitHub Release 页面，供用户自由下载。

### 3. 应用标识统一
自 v4.4.0 起，所有 Android 端包名统一迁移至 `com.androidnas.all.*`（如 `com.androidnas.all.server`），以构建更清晰的生态隔离。