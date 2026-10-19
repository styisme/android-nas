# AI 生成声明

本项目（Android NAS）在开发过程中深度使用了大型语言模型（LLM）辅助生成。为了开源协作的透明度，特此详细披露 AI 的参与程度。

## AI 参与的具体部分

- **服务端 (Server)**：
  - `NasHttpServer.java`：NanoHTTPD 的路由设计、JSON 手动拼接、Range 分片视频流、ZIP 流式解压、剪贴板内存缓存。
  - `WebDavServer.java`：纯 Socket 实现的 PROPFIND 目录遍历、PUT 流式接收、DELETE 递归删除。
  - `FtpServer.java`：纯 Socket 实现的 PASV 被动模式 FTP 协议栈。
  - `DlnaServer.java`：SSDP 组播发现与 SOAP 请求解析。
  - `index.html`：全套 MD3 CSS 变量系统、响应式 UI、AJAX 交互。

- **手机客户端**：
  - 多协议文件列表重写（原生 JSON / WebDAV XML / FTP Client）。
  - `MainActivity.java`：局域网并发扫描、相册扫描备份、UDP 协同。
  - `SyncHelper.java`：UDP 广播发现协议。

- **TV 端**：
  - 纯原生 Android 的遥控器焦点管理、虚拟全键盘搜索层、原生 VideoView 鉴权拉流。

- **手表端**：
  - 动态圆盘/方盘 Padding 适配、多协议弹窗。

- **协同与自动化**：
  - `sync_version.py` 版本自动同步逻辑、跨端 UDP 投屏发现协议。

## 开发者（人类）负责的核心工作

- **架构决策**：决定了多端同步开发方案，强制锁定了各自的最低兼容版本（服务端锁 2.2，TV 锁 4.4）。
- **兼容性攻坚**：解决了 JDK 8 与 JDK 17 的环境冲突、NanoHTTPD 的上传卡死、Android 13 权限适配。
- **真机验收**：亲自在电视盒子、旧手机、Wear OS 手表上测试了投屏与交互流程。
- **Bug 修复**：排除了各种 Lambda 表达式引发的 `IOException` 编译错误及 Activity 未注册崩溃。

## 已知的局限性

1. **极寒模式副作用**：为了极致省电，强制关闭了 LocalSend 和外网穿透。
2. **WebDAV 的正则解析**：极端特殊的私有云 WebDAV 返回格式可能会导致客户端解析失败。
3. **明文传输**：目前主要基于 HTTP 传输，若在公共网络使用请自建 SSH 穿透进行加密。

## 协议

项目代码使用 MIT 协议。