# Android NAS 底层架构深度解析 (v4.1.0)

本项目之所以能在极低兼容性（API 8）下实现极高的性能，并且横跨十个完全不同的平台与操作系统，源于底层大量抛弃了第三方重型框架，采用了**纯手写 Socket、原生硬件调用、SQLite 数据库引擎与极简设计**。

本文档将深度剖析本项目的核心架构设计，带你了解代码底层的运行原理。

---

## 一、 核心容器调度与拦截器 (HttpServerService)

服务端以 `Foreground Service`（前台服务）的形式常驻内存，在内部开启了多个独立的线程池并发监听不同端口，互不干扰：
- **8080 (HTTP JSON API)**：基于 NanoHTTPD 二次封装。在路由分发前，底层加入了**多用户权限拦截器**与**路径沙箱映射器**，处理前端鉴权、流媒体请求与数据操作。
- **8081 (WebDAV)**：**纯 Socket 手写实现**。直接解析 HTTP 报文，处理 `PROPFIND` 的 XML 拼装、`PUT` 的流式二进制写入。
- **2121 (FTP)**：**纯 Socket 手写实现**。处理 FTP 控制连接，支持 `PASV` 被动模式，管理数据通道。
- **53317 (LocalSend)**：实现 UDP 组播发现协议，并接管 HTTP 的文件流式写入。
- **8090 (UDP 协同)**：监听广播报文，用于 TV 端与手机端的局域网发现与配置下发。
- **8082 (WebSocket 远控)**：独立承载高并发的实时视频流下发与低延迟的触控指令上报。

---

## 二、 多用户隔离与 SQLite 架构 (v4.1.0 核心升级)

在 v4.1.0 中，系统从“单机玩具”蜕变为“家庭私有云”，核心在于引入了 `DatabaseManager`。

### 1. 数据库设计
服务端首次启动时，会自动在应用沙盒内生成 `nas_core.db`，包含两张核心表：
- `users`：存储用户名、密码、角色（Admin/Guest/Normal）以及专属的 `root_dir`（物理根目录）。
- `file_hashes`：记录文件 MD5 与路径的映射，用于实现 TB 级大文件的“秒传”比对。

### 2. 动态沙箱映射 (Dynamic Path Mapping)
所有的文件操作不再直接读写 `/sdcard`。
- 当请求到达时，`AuthManager` 解析 Basic Auth，获取当前用户名。
- `DatabaseManager` 查询该用户的 `root_dir`（如 `/Users/user1`）。
- `NasHttpServer.safeFile()` 拦截器会将用户请求的 `/` 强制映射到该目录，并使用 `getCanonicalPath` 校验，彻底杜绝 `../` 越权穿越。
- 仅 `admin` 账号在开启全盘模式后，可绕过此映射，直达 `/data` 和 `/system`。

---

## 三、 多媒体视界与文件处理引擎 (v4.1.0 新增)

为了告别枯燥的文件列表，底层新增了多项重型处理引擎。

### 1. 缩略图引擎 (ThumbnailManager)
- **原理**：当 Web 端请求 `/api/thumb` 时，底层利用 `BitmapFactory` 进行 `inSampleSize` 降采样，将原图压缩为 300px 的 JPEG。
- **缓存机制**：生成的缩略图会静默保存在原目录的 `.thumbnails` 隐藏文件夹中。下次请求直接读取缓存，极大降低了 CPU 负载与网络带宽。
- **时光相册**：基于此引擎，`/api/photos` 接口遍历全盘，提取照片的时间戳并倒序排列，实现了瞬间加载的瀑布流视图。

### 2. 流式打包引擎 (Streaming ZIP)
- **痛点**：传统多文件下载会占用大量临时存储空间。
- **原理**：底层利用 `ZipOutputStream`，将用户选中的多个文件通过 `FileInputStream` 分块读取并直接写入 HTTP 响应流。这实现了“边压边传”，完全不占用手机本地物理空间。

### 3. 硬件监控与安全分享
- **监控引擎**：直接读取 Linux 底层 `/proc/stat` 计算 CPU 占用率；通过带有 `Build.VERSION.SDK_INT >= 21` 守卫的 `BatteryManager` 获取电量，确保老设备不闪退。
- **分享引擎**：分享 Token 不再只是单纯的路径映射，而是以 `path|passcode|expire_time` 格式存入内存。请求时强制校验时间戳与提取码。

---

## 四、 多模态远程控制引擎 (Remote Desktop Engine)

这是项目中最硬核的底层模块，实现了一套无需外网、纯局域网内的超低延迟屏幕控制方案。

### 1. 画面捕获与编码 (H264 & Root)
- **H264 硬件推流**：利用 `MediaProjection` 获取屏幕，通过 `VirtualDisplay` 投射给 `MediaCodec`。使用 `COLOR_FormatSurface` 进行硬件级 H264 编码，生成裸流。
- **Root 截图降级**：如果未授权录屏，系统会自动降级使用 Root 权限循环执行 `screencap -p`，转换为 JPEG 格式通过 WebSocket 推流。

### 2. 前端解码与双引擎触控
- **流式传输**：网页端引入 `JMuxer.js`，接收 WebSocket 推来的 H264 裸流，实时封装为 `fMP4` 喂给 `<video>` 标签。
- **双引擎注入**：网页端鼠标事件转为 `tap,x,y` 字符串。服务端收到后，优先调用 `AccessibilityService` 模拟手势；若无障碍未开启，则降级调用 `su -c input tap` 注入。

---

## 五、 极限兼容与沙盒突破策略

- **API 8 的妥协**：为了能在十年前的设备上跑，服务端 `minSdkVersion` 锁死在 8。代码中全面废弃了 Lambda 表达式，改用传统匿名内部类（避免 AGP 4.2.2 编译报错）。
- **高版本适配守卫**：针对 Android 11+ 强制接入 `MANAGE_EXTERNAL_STORAGE`；针对 Android 13+ 引入相册媒体权限；针对电池读取 API 引入 21 版本守卫。
- **全盘突破 (Root 模式)**：在开启 Root 模式下，Java 层的 `File` 操作不再受系统沙盒校验，直接实现底层的全盘掌控。

---

## 六、 极客级保活矩阵 (Keep-Alive Matrix)

为了对付国产 ROM 严苛的杀后台机制，我们采取了四层立体防御：
1. **前台服务与锁**：`START_STICKY` + 通知栏常驻，辅以 `WakeLock` 和 `WifiLock`。
2. **无声音频欺骗**：启动 `SilentPlayer` 循环写入 0 字节 PCM，欺骗系统判定为活跃音乐播放器。
3. **定时心跳拉活**：利用 `AlarmManager` 注册心跳闹钟。
4. **Root 免死**：直接向 `/proc/<pid>/oom_score_adj` 写入 `-1000`，并冻结厂商底层杀后台服务。

---

## 七、 跨端协同协议 (UDP Discovery)

设备之间通过 `255.255.255.255:8090` 发送 `DISCOVER_TV` 广播。接收方（TV/手表）回复自身 IP。发送方拿到 IP 后，通过 HTTP POST 将密码与视频流地址直接推送到目标设备，实现秒级联动。

---

## 八、 自动化构建与全量发版矩阵 (CI/CD)

`sync_version.py` 是整个 DevOps 流程的核心枢纽。它通过正则解析 `CHANGELOG.md`，自动将提取的版本号（如 4.1.0）换算为 `versionCode`（如 40100），并动态注入到 10 个不同端的工程配置中。

### 1. 双轨制与高容错
- **日常提交**：只触发对应端的独立编译。
- **全量发版**：修改 `CHANGELOG.md` 触发矩阵。脚本使用 `find -type f` 和 `|| true` 提取产物，即使某端（如手表）编译失败，也不影响其他 9 个端发布。

### 2. 云端全平台覆盖
接入 `macos-latest` 编译苹果应用（SwiftUI），使用 OpenHarmony Docker 编译鸿蒙 HAP（ArkTS），使用 Linux/Windows 编译 Java、Python 与 C# (MAUI) 客户端。

### 3. 平铺直传
所有产物按 `Name-vVersion` 规范重命名，直接平铺展示在 GitHub Release 页面，供用户自由下载。