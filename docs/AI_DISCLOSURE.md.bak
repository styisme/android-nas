# AI 生成声明

本项目（Android NAS）在开发过程中深度使用了大型语言模型（LLM）辅助生成。我们相信透明度是开源协作的基础，因此在此详细披露 AI 的参与程度。

## AI 参与的具体部分

- **服务端 (Server)**：
  - `WebDavServer.java`：通过原生 `ServerSocket` 实现 WebDAV 协议的请求解析与 XML 响应生成。
  - `NasHttpServer.java`：NanoHTTPD 的路由设计、JSON 构造、文件流处理。
  - `index.html`：完整的 MD3 CSS 变量系统、响应式布局、AJAX 请求与弹窗交互逻辑。

- **客户端 (Client)**：
  - Android 原生 UI 布局 (XML) 与基于 Material Design 3 的组件集成。
  - `MainActivity.java`：局域网网段并发扫描算法（`ExecutorService` 线程池）。
  - `FileListActivity.java`：OkHttp 请求拦截、正则表达式解析 WebDAV XML 响应。
  - `App.java`：Material You 动态取色 (`DynamicColors.applyToActivitiesIfAvailable`) 集成。

- **工程配置**：
  - GitHub Actions 双端流水线配置、Gradle 构建脚本隔离、Python 一键生成脚本。

## 开发者（人类）负责的核心工作

- **架构决策**：决定了“网页端+客户端+WebDAV”的三位一体方案。
- **兼容性攻坚**：解决了 JDK 8 与 JDK 17 的冲突、旧版 AGP 与新版 SDK 的 XML 解析崩溃、NanoHTTPD 不支持原生 WebDAV 方法等致命问题。
- **UI/UX 验收**：亲自测试并在真机上调整了弹窗被键盘遮挡、网络请求超时提示等细节。
- **代码审查**：逐行审查了 AI 生成的网络请求代码，确保没有内存泄漏和未捕获的异常。

## 已知的局限性

由于大量代码由 AI 生成，本项仍存在以下潜在风险：
1. **极端边缘场景**：例如极小内存下的前台服务保活、某些特殊定制 ROM 的后台限制。
2. **WebDAV 兼容性**：虽然已兼容群晖和标准协议，但面对极其特殊的私有云服务时，正则解析可能失效。
3. **安全性**：WebDAV 传输目前基于 HTTP 明文，未实现 HTTPS/TLS 加密，仅建议在安全的内网使用。

## 协议

项目代码使用 MIT 协议。