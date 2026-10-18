# 贡献指南

感谢参与 Android NAS 的开发！本项目采用轻量但明确的源代码管理规范：
Conventional Commits + SemVer 2.0.0 + CHANGELOG。

## ⚠️ 关于 AI 生成代码

本项目的绝大多数代码由 AI 辅助生成。提交 PR 时请：

- 说明改动是否由 AI 生成
- 人工验证过功能（至少在真机上跑过一次）
- 如果你也是用 AI 改的，请在 PR 描述里注明

## 本地开发

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 8（必须使用 JDK 8 保证旧版编译） |
| Gradle | 6.7.1 |
| Android SDK | compileSdk 30, buildTools 30.0.3 |

### 编译命令

```bash
git clone https://github.com/styisme/android-nas.git
cd android-nas
./gradlew assembleDebug
```

### 云端构建

项目已配置 GitHub Actions：

- Push 到 `main` 或 `master` 自动编译。
- 编译产物在 Actions 页面的 `android-nas-debug` artifact 中下载。

## 项目结构

```text
android-nas/
├── app/
│   └── src/main/
│       ├── java/com/androidnas/
│       │   ├── MainActivity.java        # 本地管理 UI
│       │   ├── HttpServerService.java   # 前台服务，托管 HTTP 服务器
│       │   ├── NasHttpServer.java       # NanoHTTPD 路由与文件 API
│       │   └── BootReceiver.java        # 开机自启广播
│       ├── assets/index.html            # Web 管理界面
│       └── res/                         # 布局、字符串
├── .github/workflows/build.yml          # GitHub Actions
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 提交规范

```text
<type>(<scope>): <description>
```

### 允许的 type

- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 仅文档变更
- `style`: 格式调整（不影响逻辑）
- `refactor`: 重构
- `perf`: 性能优化
- `build`: 构建系统变更
- `ci`: CI 配置变更
- `chore`: 杂项（版本号、.gitignore）

### 常用 scope

- `ui`: 界面、本地管理
- `web`: 网页管理、HTML/JS
- `http`: NanoHTTPD 路由与 API
- `service`: 前台服务与保活
- `boot`: 开机自启
- `build`: Gradle / 工具链

### 示例

```text
feat(web): 增加文件删除按钮
fix(http): 修复上传中文文件名乱码
ui: 修复小屏设备上路径栏溢出
```

## 分支模型

| 分支 | 用途 |
|---|---|
| `master` | 稳定分支（当前默认） |
| `feat/xxx` | 功能分支 |
| `fix/xxx` | 修复分支 |
| `docs/xxx` | 文档分支 |

## Pull Request 流程

- Fork 仓库（或直接编辑）
- 新建分支：`git checkout -b feat/your-feature`
- 修改代码并本地 / Actions 验证
- 提交时用 Conventional Commits
- Push 并发起 PR
- 等 CI 通过 + Review

### PR 描述应包含

```markdown
## 变更内容
简述做了什么

## 变更类型
- [ ] feat / fix / refactor / docs / chore

## 关联 Issue
Closes #123

## 测试方式
- [ ] 本地编译通过
- [ ] 真机安装并测试（请注明安卓版本）
- [ ] Actions 编译通过

## 是否使用 AI 生成
- [ ] 是（请说明使用的模型和人工验证内容）
- [ ] 否
```

## Issue 提交

### Bug 报告

请提供：

- 设备型号 + Android 版本
- 复现步骤
- 期望行为 vs 实际行为
- 相关日志、截图

### 功能建议

- 描述使用场景
- 期望的交互方式
- 是否影响低版本兼容性（API 8+）