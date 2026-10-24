# AI 生成声明

本项目（Android NAS）在开发过程中深度使用了大型语言模型（LLM）辅助生成。为了开源协作的透明度，特此详细披露 AI 的参与程度。

**核心声明：本项目 99% 以上的代码（包括 Java、Python、HTML、Swift、ArkTS、YAML 等），均由 AI 直接输出，人类开发者在此过程中没有手写过任何一行核心业务代码。**

## AI 参与的具体部分

- **服务端核心 (Server Core) - v4.2.0 升级**：
  - `NasHttpServer.java`：NanoHTTPD 的路由设计、Range 分片视频流、ZIP 流式解压、剪贴板缓存、大文件分片上传与秒传合并逻辑、PWA 静态路由拦截，以及多用户路径拦截逻辑。
  - `WebDavServer.java` / `FtpServer.java`：纯 Socket 实现的协议栈，支持突破全盘权限。
  - `DatabaseManager.java`：SQLite 数据库引擎，实现多用户鉴权、独立根目录隔离、文件秒传哈希记录。
  - `MediaScraper.java`：免 Key 影视元数据刮削引擎，纯 Java 正则解析文件名并请求外部 API。
  - `WebhookManager.java`：轻量级 HTTP 推送中枢，支持适配 Telegram 与企业微信机器人。
  - `ThumbnailManager.java`：图片缩略图生成引擎，实现 300px 按需压缩与本地缓存。
  - `SystemMonitor.java`：硬件监控读取器，解析 `/proc/stat` 获取 CPU 占用，并加入 API 8 电池读取兼容守卫。
  - `RootManager.java` 与 `AdvancedKeepAliveManager.java`：全套 Root/ADB 保活注入、OOM 免死写入与系统级命令执行。
  - `RemoteDesktopManager.java` / `NasAccessibilityService.java`：基于 `MediaCodec` 的 H264 硬件编码推流、WebSocket 服务端搭建，以及无障碍触控与 Root 指令注入双引擎。
  - `index.html`：全套 MD3 CSS 变量系统、响应式 UI、Bottom Sheet 交互逻辑、SparkMD5 分片计算、基于 `JMuxer.js` 的 H264 实时解码，以及时光相册、影视海报墙与 PWA 注册代码。

- **跨平台客户端 (Multi-Platform Clients)**：
  - **手机端 (Android)**：相册自动备份逻辑（MediaStore + 增量比对）、多协议文件列表重写、局域网扫描与投屏、WebView 远控套壳与系统分享外链。
  - **电视端 / 手表端**：全面改用 Leanback / RecyclerView 卡片流与焦点动画，动态 Padding 适配与快捷操作菜单。
  - **桌面端 (Python) / CLI 端**：Tkinter 扁平化 UI 重写、全局搜索、剪贴板同步、基于 `cmd` 模块的交互式 Shell 与 Tab 补全。
  - **苹果端 (SwiftUI) / 鸿蒙端 (ArkTS)**：原生声明式 UI 编写，全端对齐搜索、剪贴板与系统级 Want/OpenURL 跳转。
  - **微信小程序端**：WXML/WXSS/JS 全量编写，免密分享短链生成与系统剪贴板调用。

- **CI/CD 自动化 (DevOps)**：
  - 基于 GitHub Actions 的全量发版矩阵与平铺直传机制。
  - 基于 GitHub Secrets 的动态 Keystore 解码与 Release 签名注入。
  - `sync_version.py` 版本自动注入脚本、`.debug` 包名后缀分流控制与 `paths-ignore` 路径隔离策略。

## 开发者（人类）负责的核心工作

- **产品定义与需求决策**：决定项目要包含哪些端、用什么协议、UI 风格长什么样，以及决定多用户和媒体库的演进方向。
- **编译与执行**：在无电脑的纯 Android 环境下，使用 Pydroid 运行 AI 生成的 Python 覆盖脚本，使用 Mgit 提交代码并推送至 GitHub。
- **真机测试与反馈**：把编译好的 APK 安装到废旧手机、电视盒子、手表上，测试远控、投屏、文件管理等功能是否正常，并将错误日志或闪退截图反馈给 AI，由 AI 输出修复补丁。
- **兼容性与编译排雷**：
  - 发现并反馈因引入 Lambda 表达式导致 AGP 4.2.2 编译失败的问题。
  - 发现并反馈因 `BatteryManager` 导致 API 8 闪退的问题。
  - 敏锐捕捉并反馈了 `targetSdkVersion 22` 在 Release 构建时触发的 `lintVitalRelease` 致命拦截，以及 Gradle 脚本中 `signingConfig` 被正则错误嵌套导致的 `Could not find method` 崩溃，促使 AI 给出修复补丁。
- **问题排查**：解决因添加 Root 保活模块后，系统 PMS 被 Hook 导致第三方安装器提示“未卸载”的冲突问题。
- **最终验收**：决定某个版本是否达到发布标准。

## 已知的局限性

1. **边缘场景依赖人类测试**：AI 无法真机运行，因此必须依靠人类反复测试并反馈 Bug（例如 H264 推流在不同 SOC 上的兼容性、SQLite 多线程并发锁表现）。
2. **Root 模式的危险性**：全盘模式与 Web Shell 拥有极高的系统权限，操作不当可能导致设备变砖或数据丢失。
3. **明文传输**：目前局域网主要基于 HTTP 传输，若在公共网络使用请自建 SSH 穿透进行加密，或开启自签 HTTPS 框架。
4. **签名与生态限制**：苹果与鸿蒙端的云端编译产物无法绕过官方的实名签名限制，只能输出未签名的测试包。

## 协议

本项目遵循 MIT 协议。