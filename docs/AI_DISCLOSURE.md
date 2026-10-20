# AI 生成声明

本项目（Android NAS）在开发过程中深度使用了大型语言模型（LLM）辅助生成。为了开源协作的透明度，特此详细披露 AI 的参与程度。

**核心声明：本项目 99% 以上的代码（包括 Java、Python、HTML、YAML 等），均由 AI 直接输出，人类开发者在此过程中没有手写过任何一行核心业务代码。**

## AI 参与的具体部分

- **服务端 (Server)**：
  - `NasHttpServer.java`：NanoHTTPD 的路由设计、Range 分片视频流、ZIP 流式解压、剪贴板缓存、全量控制台 API。
  - `WebDavServer.java` / `FtpServer.java`：纯 Socket 实现的协议栈，支持突破全盘权限。
  - `RootManager.java` 与 `AdvancedKeepAliveManager.java`：全套 Root/ADB 保活注入与命令执行。
  - `index.html`：全套 MD3 CSS 变量系统、响应式 UI、Bottom Sheet 交互逻辑。

- **跨平台客户端**：
  - 手机端：相册自动备份逻辑（MediaStore + 增量比对）、多协议文件列表重写、局域网扫描与投屏。
  - 电视端 / 手表端：全面改用 Leanback / RecyclerView 卡片流与焦点动画。
  - 桌面端 / CLI 端：Tkinter 扁平化重写、基于 `cmd` 模块的交互式 Shell 与 Tab 补全。

- **CI/CD 自动化**：
  - GitHub Actions 全量发版矩阵、路径隔离策略、`sync_version.py` 版本自动注入脚本。

## 开发者（人类）负责的核心工作

- **产品定义与需求决策**：决定项目要包含哪些端，用什么协议，UI 风格长什么样。
- **编译与执行**：在 Termux 环境中运行 AI 生成的 Python 覆盖脚本，使用 Mgit 提交代码并推送。
- **真机测试与反馈**：把编译好的 APK 安装到废旧手机、电视盒子、手表上，测试功能是否正常，并将错误日志或截图反馈给 AI，由 AI 输出修复补丁。
- **最终验收**：决定某个版本是否达到发布标准。

## 已知的局限性

1. **边缘场景依赖人类测试**：AI 无法真机运行，因此必须依靠人类反复测试并反馈 Bug。
2. **Root 模式的危险性**：全盘模式与 Web Shell 拥有极高的系统权限，操作不当可能导致设备变砖。
3. **明文传输**：目前主要基于 HTTP 传输，若在公共网络使用请自建 SSH 穿透进行加密。

## 协议

本项目遵循 MIT 协议。