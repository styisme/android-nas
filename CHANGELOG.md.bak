# Changelog

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-10-19

首个正式版本，实现局域网网页管理、本地管理、开机自启和自动化构建。

### Added
- 局域网网页管理：内置 NanoHTTPD，浏览器访问 `http://手机IP:8080`。
- 文件浏览：目录逐级浏览，目录优先排序。
- 文件下载：`GET /api/download`，支持 Content-Disposition 文件名。
- 文件上传：`POST /api/upload`，支持上传到当前目录。
- 本地管理 UI：服务状态、IP 显示、启动/停止、目录浏览、上级/刷新。
- 本地文件操作：打开、重命名、删除、属性、新建文件夹。
- 开机自启：`BOOT_COMPLETED` 静态广播。
- 前台服务保活：常驻通知 + `START_STICKY`。
- GitHub Actions 自动构建：JDK 8 + Gradle 6.7.1，推送即构建。
- Python 一键生成脚本：生成项目、Gradle Wrapper、git 初始化，可选自动创建 GitHub 仓库并推送。
- `minSdkVersion 8`，最低支持 Android 2.2。

### Known Limitations
- 首次安装需手动打开一次 App，之后开机自启才生效。
- 明文 HTTP，无 TLS。
- `targetSdkVersion 22` 依赖系统兼容模式授予存储权限。