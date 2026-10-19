# AI 生成声明

本项目（Android NAS）在开发过程中深度使用了大型语言模型（LLM）辅助生成。我们相信透明度是开源协作的基础，因此在此详细披露 AI 的参与程度。

## AI 参与的具体部分

- **服务端 (Server)**：
  - `WebDavServer.java`：通过原生 `ServerSocket` 从零实现了 WebDAV 协议的请求解析，包括 `PROPFIND` 的 XML 构造、`PUT` 的流式接收。
  - `NasHttpServer.java`：NanoHTTPD 的路由设计、免密分享 Token 生成、正则搜索提取。
  - `index.html`：MD3 CSS 变量系统、拖拽上传逻辑、进度条事件监听、highlight.js 集成。

- **客户端 (Client)**：
  - `FileListActivity.java`：多选逻辑、瀑布流布局切换、WebDAV XML 正则解析、上传下载进度条监听。
  - `MainActivity.java`：局域网网段并发扫描算法（`ExecutorService` 与子网 IP 推导）。

- **桌面端 (Desktop)**：
  - `nas_client.py`：Tkinter 原生 UI 构建、多线程文件上传/下载同步。

- **工程配置**：
  - GitHub Actions 三端流水线配置、Gradle 构建脚本隔离。

## 开发者（人类）负责的核心工作

- **架构决策**：决定了“三端一体”的方案，并在多次重构中指明了功能保留与合并的方向。
- **兼容性攻坚**：解决了服务端 JDK 8 与客户端 JDK 17 的环境冲突、旧版 AGP 与新版 SDK 的 XML 解析崩溃、NanoHTTPD 2.2.0 缺少 `Content-Length` 导致的上传卡死问题。
- **UI/UX 验收**：亲自测试并在真机上调整了弹窗被键盘遮挡、网络请求超时的 Toast 提示等细节。

## 已知的局限性

由于大量代码由 AI 生成，本项目仍存在以下潜在风险：
1. **HTTPS 兼容性**：由于 Android 极低版本（API < 14）缺少动态生成 BKS 证书库的 API，服务端开启 HTTPS 加密开关时，在老机型上可能无法成功建立 SSL 连接。
2. **WebDAV 兼容性**：虽然已兼容群晖和标准协议，但面对极其特殊的私有云服务时，正则解析可能失效。
3. **安全性**：HTTP Basic Auth 通过明文（或 Base64）传输，若未开启 HTTPS 开关，在公共 Wi-Fi 下存在被抓包的风险，仅建议在安全的内网使用。

## 协议

项目代码使用 MIT 协议。