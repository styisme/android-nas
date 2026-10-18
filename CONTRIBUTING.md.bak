# 贡献指南

感谢参与 Android NAS 的开发！无论你是修复 Bug、提出新功能，还是优化文档，都非常欢迎。

## ⚠️ 关于 AI 生成代码

本项目大量使用了大型语言模型（LLM）辅助编写。提交 PR 时，请务必遵守以下规范：
1. 在 PR 描述中注明代码是否由 AI 生成。
2. 提交前必须在模拟器或真机上跑通（请注明测试机型的 Android 版本）。
3. 如果是修复 Bug，请附带复现步骤和错误日志。

## 📂 项目结构

本仓库包含两个相互独立的工程：

```text
android-nas/
├── app/                         # 服务端 (Server)
│   └── src/main/
│       ├── java/com/androidnas/
│       │   ├── MainActivity.java        # 本地管理界面
│       │   ├── HttpServerService.java   # 服务容器 (托管 8080/8081)
│       │   ├── NasHttpServer.java       # NanoHTTPD 网页与 API
│       │   ├── WebDavServer.java        # 原生 WebDAV 协议栈
│       │   └── BootReceiver.java        # 开机广播
│       ├── assets/index.html            # MD3 网页端
│       └── res/                         # 布局、字符串
├── client/                      # 客户端 (Client)
│   └── src/main/
│       ├── java/com/androidnas/client/
│       │   ├── MainActivity.java        # 首页 (扫描/多设备)
│       │   ├── FileListActivity.java    # 文件管理 (支持双协议)
│       │   ├── SettingsActivity.java    # 设置中心
│       │   ├── App.java                 # 动态取色初始化
│       │   └── NasDevice.java           # 数据模型
│       └── res/
├── .github/workflows/           # 双端独立 Actions
│   ├── build.yml                # 构建服务端
│   └── build-client.yml         # 构建客户端
├── build.gradle                 # 服务端构建 (AGP 4.2.2)
└── client/build.gradle          # 客户端构建 (AGP 7.4.2)
```

## 🛠 本地编译环境

因为服务端要兼容极老的 Android 2.2，而客户端要使用最新的 MD3 库，所以两边的构建工具链完全不同，**请勿修改成一致**。

### 编译服务端
- 环境：JDK 8, Gradle 6.7.1
- 命令：
  ```bash
  ./gradlew assembleDebug
  ```

### 编译客户端
- 环境：JDK 11, Gradle 7.5
- 命令：
  ```bash
  cd client
  ./gradlew assembleDebug
  ```

## 📝 提交规范

使用 `Conventional Commits` 规范：

```text
<type>(<scope>): <description>
```

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
- `ci`: Actions 构建

## 🚀 Pull Request 流程

1. Fork 本仓库并新建分支 `feat/your-feature`。
2. 编码并在本地验证（务必测试编译是否通过）。
3. Push 到你的仓库，并向 `main` 分支发起 PR。
4. 确保 GitHub Actions 双端构建均亮起绿灯。