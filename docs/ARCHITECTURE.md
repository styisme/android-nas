# Android NAS 底层架构深度解析

本项目之所以能在极低兼容性（API 8）下实现极高的性能，并且横跨十个完全不同的平台与操作系统，源于底层大量抛弃了第三方重型框架，采用了**纯手写 Socket、原生硬件调用与极简设计**。

本文档将深度剖析本项目的核心架构设计，带你了解代码底层的运行原理。

---

## 一、 核心容器调度 (HttpServerService)

服务端以 `Foreground Service`（前台服务）的形式常驻内存，在内部开启了多个独立的线程池并发监听不同端口，互不干扰：
- **8080 (HTTP JSON API)**：基于 NanoHTTPD 二次封装，处理 Web 前端、手机端、桌面端的增删改查与流媒体请求。
- **8081 (WebDAV)**：**纯 Socket 手写实现**。直接解析 HTTP 报文，处理 `PROPFIND` 的 XML 拼装、`PUT` 的流式二进制写入、`MKCOL` 建目录等。
- **2121 (FTP)**：**纯 Socket 手写实现**。处理 FTP 控制连接，支持 `PASV` 被动模式（解决老电视和 Windows 资源管理器连不上的问题），管理数据通道的 Socket 流转发。
- **53317 (LocalSend)**：实现 UDP 组播发现协议，并接管 HTTP 的文件流式写入。
- **8090 (UDP 协同)**：监听广播报文，用于 TV 端与手机端的局域网发现与配置下发。
- **8082 (WebSocket 远控)**：**v4.0.0 新增模块**。独立承载高并发的实时视频流下发与低延迟的触控指令上报。

---

## 二、 多模态远程控制引擎 (Remote Desktop Engine)

这是项目中最硬核的底层模块，实现了一套无需外网、纯局域网内的超低延迟屏幕控制方案。

### 1. 画面捕获与编码 (H264 & Root)
- **H264 硬件推流 (主方案)**：
  利用 Android 原生的 `MediaProjection` 获取屏幕画面，通过 `VirtualDisplay` 将屏幕内容投射给 `MediaCodec`。我们使用 `COLOR_FormatSurface` 进行硬件级 H264 编码，生成裸流，帧率 30fps，码率 4Mbps，极大降低了 CPU 负担。
- **Root 截图降级 (备用方案)**：
  如果用户在 Android 11+ 上未授权录屏，系统会自动降级使用 Root 权限循环执行 `screencap -p`。画面转换为 JPEG 格式后通过 WebSocket 推流，实现“零授权、免打扰”的屏幕监控。

### 2. 流式传输与前端渲染
- **WebSocket 独立服务**：在 8082 端口新增了 WebSocket 服务，专门用于视频流下发与指令上报。
- **前端解码**：网页端引入了 `JMuxer.js` 轻量级库。它接收来自 WebSocket 的 H264 裸流，实时将其封装为 `fMP4`（Fragmented MP4）格式，直接喂给原生的 `<video>` 标签进行硬解渲染。

### 3. 双引擎触控注入 (Dual-Engine Touch Injection)
网页端的鼠标事件经过坐标映射后，通过 WebSocket 发送字符串指令（如 `tap,x,y`）。服务端收到指令后，执行双引擎自适应路由：
- **无障碍引擎**：利用 `AccessibilityService.dispatchGesture()` 构建 `GestureDescription`，精准模拟滑动与点击（适用于未 Root 设备）。
- **Root 引擎**：底层直接调用 `Runtime.exec("su -c input tap x y")`，无视一切系统限制（适用于已 Root 设备）。

---

## 三、 极限兼容与沙盒突破

- **API 8 的妥协**：为了能在十年前的设备上跑，服务端 `minSdkVersion` 锁死在 8，`targetSdkVersion` 锁死在 22（绕过 Android 6.0 的动态权限）。所有高版本 API（如 `MulticastLock` 需要 API 12）都通过 `Build.VERSION.SDK_INT` 做了严密的判断包裹。
- **Android 11+ 存储适配**：在保持老系统兼容的同时，针对高版本系统完美接入了 `MANAGE_EXTERNAL_STORAGE`（所有文件访问权限），确保读写无障碍。
- **全盘突破 (Root 模式)**：在开启 Root 模式下，Java 层的 `File` 操作不再受系统沙盒校验。WebDAV 和 FTP 的根目录从 `/sdcard` 动态切换为 `/`，直接实现底层的全盘掌控，直达 `/data` 和 `/system`。

---

## 四、 极客级保活矩阵 (Keep-Alive Matrix)

为了对付国产 ROM 严苛的杀后台机制，我们采取了四层立体防御：

1. **Layer 1 - 前台服务与锁**：标准的 `START_STICKY` + 通知栏常驻，辅以 `WakeLock` (CPU锁) 和 `WifiLock` (高性能 Wi-Fi 锁)。
2. **Layer 2 - 无声音频欺骗**：启动 `SilentPlayer` 循环写入 0 字节的 PCM 音频流，欺骗系统将其判定为活跃的音乐播放器，大幅降低被杀概率。
3. **Layer 3 - 定时心跳拉活**：利用 `AlarmManager` 注册每 5 分钟一次的唤醒闹钟，进程一旦死亡，立刻被闹钟拉起。
4. **Layer 4 - Root 免死（终极杀招）**：通过 Root 权限，直接向 `/proc/<pid>/oom_score_adj` 写入 `-1000`，获得比系统桌面还高的优先级，并支持一键生成 Magisk 模块，直接冻结厂商底层的杀后台服务（如 `com.miui.powerkeeper`）。

---

## 五、 跨端协同协议 (UDP Discovery)

在没有外网的情况下，多台设备如何实现局域网内的秒级联动？
1. 手机向 `255.255.255.255:8090` 发送 UDP 广播报文：`DISCOVER_TV`。
2. 电视端/手表端收到后，向手机单播回复自己的 IP：`TV_HERE`。
3. 手机拿到 IP 后，直接向电视发送 HTTP POST 请求，将 NAS 的 IP 和密码传给电视，实现秒级配置同步，无需用户使用遥控器输入复杂的密码。
4. 同理，手机向电视发送视频绝对 URL（`/play?url=xxx`），电视端直接拉起播放器拉流播放。

---

## 六、 自动化构建与全量发版矩阵 (CI/CD)

`sync_version.py` 是整个 DevOps 流程的核心枢纽。它通过正则解析 `CHANGELOG.md`，自动将提取的版本号（如 4.0.0）换算为 `versionCode`（如 40000），并在编译前动态注入到 10 个不同端的工程配置中，确保所有端的版本号永远一致。

### 1. 双轨制流水线
- **日常代码提交**：修改 `.java` / `.py` / `.c` 等代码时，只触发对应端的独立编译（配置了忽略 `.md` 文档变更）。
- **全量发版打包**：当修改 `CHANGELOG.md` 时，触发 `build-all.yml` 矩阵，并行启动 10 个不同环境的 Job。

### 2. 云端全平台覆盖
- 成功接入 `macos-latest` Runner 编译苹果应用（SwiftUI）。
- 使用基于 OpenHarmony 的 Docker 镜像编译鸿蒙 HAP（ArkTS）。
- 使用 Ubuntu 与 Windows Runner 编译 Java 服务端、Android 客户端、Python 桌面端、C# (MAUI) 客户端以及微信小程序。

### 3. 高容错平铺直传
全面废弃 `cp *` 这种粗暴拷贝，改为 `find -type f` 提取文件，并加入 `|| true` 容错机制。即使某一个小众端（如手表端）编译失败，也不会阻断其他 9 个端的顺利发版。最终，所有产物会按规范重命名，并直接平铺上传至 GitHub Release 页面，供用户自由下载。