# Android NAS 底层架构深度解析 (v4.5.0)

本项目之所以能在极低兼容性（API 8）下实现极高的性能，并且横跨十个完全不同的平台与操作系统，源于底层大量抛弃了第三方重型框架，采用了**纯手写 Socket、原生硬件调用、SQLite 数据库引擎与极简设计**。

本文档将深度剖析本项目的核心架构设计，带你了解代码底层的运行原理。

---

## 一、 核心容器调度与多重拦截器 (HttpServerService)

服务端以 `Foreground Service`（前台服务）的形式常驻内存，在内部开启了多个独立的线程池并发监听不同端口，互不干扰：
- **8080 (HTTP / HTTPS)**：基于 NanoHTTPD 二次封装。在路由分发前，底层加入了**多重安全拦截器**（隐藏暗号、局域网锁、Fail2Ban、CC限流、2FA验证等）、**多用户权限校验**以及**路径沙箱映射器**。处理前端鉴权、流媒体请求、PWA 静态资源分发与数据操作。
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
- `users`：存储用户名、密码、角色（Admin/Guest/Normal）、专属的 `root_dir`（物理根目录）以及**紧急安全码（PIN）**。所有密码均采用 **SHA-256 + 随机盐（Salt）** 加密存储。
- `file_hashes`：记录文件 MD5 与路径的映射，用于实现 TB 级大文件的“秒传”比对。

### 2. 高并发身份隔离 (ThreadLocal)
为避免多线程池下的请求身份串号（越权），`AuthManager` 引入了 `ThreadLocal`。在处理 HTTP、WebDAV、FTP 请求的线程结束时，**必须**调用 `clearCurrentUser()` 清理状态，防止下一个复用该线程的请求意外继承前一个用户的管理员权限。

### 3. 动态沙箱映射与逃逸修复
所有的文件操作不再直接读写 `/sdcard`。
- 拦截器会将用户请求的路径强制映射到其专属的 `root_dir`（如 `/Users/user1`），并使用 `getCanonicalPath` 校验。
- **v4.5.0 核心修复**：彻底移除了 `new File(rootDir, path)` 中因 `path` 携带前导斜杠（如 `/xxx`）导致父目录被忽略的底层缺陷，强制剥离前导斜杠，杜绝越权穿越与全站 404 问题。
- 仅 `admin` 账号在开启全盘模式后，可绕过此映射，直达 `/data` 和 `/system`。

---

## 三、 企业级五重安全防线底座 (v4.5.0 全新架构)

本版本在底层的 `SecurityFilter` 与 `NasHttpServer` 之间构筑了一道极深的护城河，纯 Java 原生实现，没有引入任何第三方安全框架。

### 1. 原生 2FA 动态口令 (TotpManager)
- 底层自行手写了基于 **HMAC-SHA1** 的 TOTP (RFC 6238) 算法。
- 支持与 Google Authenticator 等标准验证器扫码互通。
- 在 `AuthManager.verifyRole` 中加入双重校验逻辑，如果开启了 2FA，除了账号密码外，还必须校验 HTTP Header 中的 `X-TOTP-Code`。

### 2. 全局隐藏暗号与局域网物理锁
- **隐藏入口**：如果用户设置了 `SecretPath`，所有非 API 请求必须携带 `?s=暗号`，否则直接伪装返回 404。
- **高危 API 局域网锁**：针对 `/api/shell`、`/api/settings` 等，通过 `isLanIp()` 判断来源 IP。若不是内网 IP 且用户开启了局域网锁，直接返回 403，物理阻断公网提权。

### 3. 算数验证码与防 CC 限流
- **算数验证码**：触发防爆破后，服务端动态生成随机算数题，并缓存至 `ConcurrentHashMap`。前端需携带 `X-Captcha` Header 才能继续请求。
- **防 CC 限流**：`SecurityFilter` 引入了滑动窗口计数法，单 IP 每秒请求超限直接熔断封禁，保护低配旧手机不被恶意刷流量卡死。

---

## 四、 短信中枢与网络探针引擎 (v4.5.0 新增)

为了榨干闲置手机的剩余价值，底层新增了极其硬核的硬件与网络接管能力。

### 1. 短信中枢 (SmsManager)
- 基于 `BroadcastReceiver` 监听 `SMS_RECEIVED` 广播，瞬间提取发件人与正文。
- 利用原生的 `android.telephony.SmsManager` 实现网页端反向发送短信。
- 短信数据不仅缓存在内存中供前端查看，更直接触发 `WebhookManager` 推送到用户的主力机。

### 2. 服务端口探针 (Port Scanner)
- 为了监控局域网的运行状态，底层新增 `/api/ports` 接口。
- 采用 `ExecutorService` 线程池，对指定的端口列表（如 Jellyfin、Alist）开启并发 `Socket.connect()` 探测，300ms 超时断连，精准测出各服务的存活状态与 TCP 延迟。

### 3. 极限网络测速引擎 (Speedtest)
- 为测试 NAS 与路由器的真实带宽，新增 `SpeedTestStream` 内部类。
- 该流继承自 `InputStream`，不占用任何真实的磁盘 I/O，直接利用内存生成 0 字节填充流（默认 20MB），以最高速度通过 HTTP 响应下发给前端进行带宽压测。

---

## 五、 挂机防护与媒体处理引擎

### 1. 电池温度熔断哨兵 (BatteryProtector)
- 专为旧手机 7x24 小时插电设计。底层利用 `BroadcastReceiver` 持续监听 `ACTION_BATTERY_CHANGED`。
- 一旦捕捉到物理温度突破 45℃，立即触发 Webhook 报警；若开启了 Root，直接向 `/sys/class/power_supply/battery/charging_enabled` 写入 0，强制切断充电，物理防止电池鼓包。

### 2. 影视元数据刮削与缩略图
- **极简原理**：采用纯 Java 正则对文件名切片，利用 `HttpURLConnection` 请求免 Key API 获取海报。
- **缩略图引擎**：利用 `BitmapFactory.Options.inSampleSize` 降采样，将原图压缩为 300px 的 JPEG，并静默缓存至 `.thumbnails` 隐藏文件夹。

### 3. 大文件秒传与流式打包
- 前端计算 MD5 后，查询 SQLite 实现 0 秒秒传。
- 流式打包底层利用 `ZipOutputStream`，边压边写 HTTP 响应流，杜绝本地临时空间占用。

---

## 六、 多模态远程控制引擎 (Remote Desktop Engine)

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

## 七、 极限兼容与沙盒突破策略

- **API 8 的妥协**：为了能在十年前的设备上跑，服务端 `minSdkVersion` 锁死在 8。代码中全面废弃了 Lambda 表达式，改用传统匿名内部类（避免 AGP 4.2.2 编译报错）。
- **NanoHTTPD 状态码降级**：由于底层依赖的 `NanoHTTPD 2.2.0` 不包含 429 (Too Many Requests) 与 503 (Service Unavailable)，底层安全拦截统一降级返回 `403 FORBIDDEN`，完美兼容且语义通顺。
- **全盘突破 (Root 模式)**：在开启 Root 模式下，Java 层的 `File` 操作不再受系统沙盒校验，直接实现底层的全盘掌控。

---

## 八、 极客级保活矩阵 (Keep-Alive Matrix)

为了对付国产 ROM 严苛的杀后台机制，我们采取了四层立体防御：
1. **前台服务与锁**：`START_STICKY` + 通知栏常驻，辅以 `WakeLock` 和 `WifiLock`。
2. **无声音频欺骗**：启动 `SilentPlayer` 循环写入 0 字节 PCM，欺骗系统判定为活跃音乐播放器。
3. **定时心跳拉活**：利用 `AlarmManager` 注册心跳闹钟。
4. **Root 免死**：直接向 `/proc/<pid>/oom_score_adj` 写入 `-1000`，并冻结厂商底层杀后台服务。

---

## 九、 自动化构建与全量发版矩阵 (CI/CD)

`sync_version.py` 是整个 DevOps 流程的核心枢纽。它通过正则解析 `CHANGELOG.md`，自动将提取的版本号换算为 `versionCode`，并动态注入到 10 个不同端的工程配置中。

### 1. 双轨制构建与签名机制
- **日常调试**：只触发对应端的独立编译（`assembleDebug`），包名自动追加 `.debug` 后缀，防止覆盖正式版。
- **全量发版**：修改 `CHANGELOG.md` 触发矩阵（`assembleRelease`）。通过 GitHub Secrets 注入 `release.keystore` 完成正式签名。

### 2. 高容错与平铺直传
- 脚本使用 `find -type f` 和 `|| true` 提取产物，即使某端编译失败，也不影响其他端的发布。
- 废弃了容易在 YAML 中引发语法崩溃的 Here-Document (<<EOF)，改用 `printf` 追加生成 Release Notes，彻底解决发版环节报错。
- 所有产物按 `Name-vVersion` 规范重命名，直接平铺展示在 GitHub Release 页面，供用户自由下载。

### 3. 应用标识统一
所有 Android 端包名统一迁移至 `com.androidnas.all.*`（如 `com.androidnas.all.server`），以构建更清晰的生态隔离。