# Android NAS 底层架构深度解析

本项目之所以能在极低兼容性（API 8）下实现极高的性能，源于底层大量抛弃了第三方库，采用了纯手写 Socket 与原生调用的硬核架构。

## 一、 核心容器调度 (HttpServerService)

服务端以 `Foreground Service` 的形式常驻内存，在其中开启了多个独立的线程池并发监听不同端口：
- **8080 (HTTP JSON API)**：基于 NanoHTTPD 二次封装，处理 Web 前端、手机端的增删改查请求。
- **8081 (WebDAV)**：**纯 Socket 手写实现**。直接解析 HTTP 报文，处理 `PROPFIND` 的 XML 拼装、`PUT` 的流式二进制写入、`MKCOL` 建目录等。
- **2121 (FTP)**：**纯 Socket 手写实现**。处理 FTP 控制连接，支持 `PASV` 被动模式（解决老电视和 Windows 资源管理器连不上的问题），管理数据通道的 Socket 流转发。
- **53317 (LocalSend)**：实现 UDP 组播发现协议，并接管 HTTP 的文件流式写入。
- **8090 (UDP 协同)**：监听广播报文，用于 TV 端与手机端的局域网发现与配置下发。

## 二、 极限兼容与绕过沙盒

- **API 8 的妥协**：为了能在十年前的设备上跑，服务端 `minSdkVersion` 设为 8，`targetSdkVersion` 锁死在 22（绕过 Android 6.0 的动态权限）。所有高版本 API 都通过 `Build.VERSION.SDK_INT` 做了严密的判断包裹。
- **全盘突破**：在开启 Root 模式下，Java 层的 `File` 操作不再被校验。WebDAV 和 FTP 的根目录从 `/sdcard` 动态切换为 `/`，直接实现底层的全盘掌控。

## 三、 极客级保活矩阵

为了对付国产 ROM 的杀后台，我们采取了四层立体防御：
1. **Layer 1 - 前台服务与锁**：标准的 `START_STICKY` + 通知栏常驻，辅以 `WakeLock` (CPU锁) 和 `WifiLock` (高性能 Wi-Fi 锁)。
2. **Layer 2 - 无声音频**：启动 `SilentPlayer` 循环写入 0 字节的 PCM 音频流，欺骗系统将其判定为活跃的音乐播放器，大幅降低被杀概率。
3. **Layer 3 - 定时心跳**：利用 `AlarmManager` 注册每 5 分钟一次的唤醒闹钟，进程一旦死亡，立刻被闹钟拉起。
4. **Layer 4 - Root 免死（终极杀招）**：通过 Root 权限，直接向 `/proc/<pid>/oom_score_adj` 写入 `-1000`，获得比系统桌面还高的优先级，并直接冻结厂商底层的杀后台服务（如 `com.miui.powerkeeper`）。

## 四、 跨端协同协议

在没有外网的情况下，手机如何控制电视？
1. 手机向 `255.255.255.255:8090` 发送 UDP 广播报文：`DISCOVER_TV`。
2. 电视端收到后，向手机单播回复自己的 IP：`TV_HERE`。
3. 手机拿到 IP 后，直接向电视发送 HTTP POST 请求，将 NAS 的 IP 和密码传给电视，实现秒级配置同步。
4. 同理，手机向电视发送视频绝对 URL，电视端直接拉起播放器拉流。

## 五、 自动化构建与版本控制

`sync_version.py` 是 DevOps 的核心。它通过正则解析 `CHANGELOG.md`，自动将提取的版本号（如 3.3.0）换算为 `versionCode`（如 30300），并在编译前动态注入到 7 个不同端的工程文件（`build.gradle` / `pubspec.yaml` 等）中，确保所有端的版本号永远一致。