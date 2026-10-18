# 贡献指南

感谢参与 Android NAS 的开发！这是一个跨越了十年的技术栈缝合怪项目，我们采用轻量但明确的源代码管理规范。

## ⚠️ 关于 AI 生成代码

本项目的绝大多数代码由 AI 辅助生成。提交 PR 时请：
1. 说明改动是否由 AI 生成。
2. 人工验证过功能（至少在真机上跑过一次）。
3. 如果你是用 AI 改的，请在 PR 描述里附上你使用的提示词（Prompt）。

## 📂 项目结构

```text
android-nas/
├── app/                         # 服务端源码
│   └── src/main/
│       ├── java/com/androidnas/
│       │   ├── MainActivity.java        # 服务端本地 UI
│       │   ├── HttpServerService.java   # 前台服务（托管 8080/8081）
│       │   ├── NasHttpServer.java       # NanoHTTPD (JSON API & Web)
│       │   ├── WebDavServer.java        # 原生 Socket WebDAV 栈
│       │   └── BootReceiver.java        # 开机广播
│       └── assets/index.html            # MD3 网页端
├── client/                      # 客户端源码
│   └── src/main/
│       ├── java/com/androidnas/client/
│       │   ├── MainActivity.java        # 首页 (局域网扫描/多设备)
│       │   ├── FileListActivity.java    # 文件管理 (JSON/WebDAV 双协议)
│       │   └── SettingsActivity.java    # 设置中心 (主题/暗黑模式)
│       └── res/                         # MD3 布局与主题
├── .github/workflows/           # 双端独立 Actions
└── docs/                        # 文档与说明
```

## 🛠 本地编译环境隔离

因为服务端要兼容古老的 Android 2.2，而客户端要使用现代的 MD3 库，两边的构建工具链完全不同，**请绝对不要试图统一它们的版本**。

### 编译服务端
- 环境：JDK 8, Gradle 6.7.1, AGP 4.2.2
- 命令：
  ```bash
  ./gradlew assembleDebug
  ```

### 编译客户端
- 环境：JDK 11, Gradle 7.5, AGP 7.4.2
- 命令：
  ```bash
  cd client
  ./gradlew assembleDebug
  ```

## 📝 提交规范

采用 Conventional Commits 规范。写法：`<type>(<scope>): <description>`

- `feat`: 新功能
- `fix`: 修复 Bug
- `docs`: 文档变更
- `style`: 代码格式（不影响逻辑）
- `refactor`: 重构
- `chore`: 构建/依赖更新

**Scope 常用值**：
- `server`: 服务端代码
- `web`: 网页端 (index.html)
- `client`: 客户端代码
- `ui`: 界面美化
- `ci`: GitHub Actions

## 🚀 Pull Request 流程

1. Fork 本仓库并从 `main` 分支新建功能分支，如 `feat/lan-speed-test`。
2. 编码并在本地运行 `./gradlew assembleDebug` 验证编译。
3. 在真机上测试通过（如涉及 UI，请附带截图）。
4. Push 并提交 PR，确保 GitHub Actions 双端构建亮起绿灯。