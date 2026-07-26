# 闲鱼管理器 · Electron 桌面版

基于 Electron 28 的跨平台桌面应用，支持两种完整分发形态：

| 形态 | 模式 | 适用渠道 | 后端运行方式 |
|---|---|---|---|
| 官网版 | `local` | 官网 DMG/ZIP/Windows 安装包 | Electron 启动内置 Spring Boot JAR，可内嵌 JRE |
| App Store 版 | `cloud` | Mac App Store | Electron 只连接云端后台基础 URL，不启动本地 Java/Chrome/OpenList |

## 功能特性

- 🚀 **双模式后台** — `local` 本地后端 / `cloud` 云端后台 URL
- 📌 **系统托盘** — 官网版支持最小化到托盘（MAS 自动禁用）
- 🌐 **内嵌 Web UI** — local 加载 `127.0.0.1:{port}`，cloud 加载远程后台 URL
- 🔄 **健康检查** — 启动后等待后台就绪再加载 UI
- 📝 **日志管理** — Electron 日志与后端日志写入用户数据目录
- ⚙️ **设置面板** — 后台模式、云端 URL、本地端口、Java 路径、自启等配置
- 🍎 **App Store 准备** — 已提供 `mas` target 与 sandbox entitlements

## 目录结构

```text
scripts/electron/
├── build.sh
├── package.json
├── main.js
├── preload.js
├── renderer/
│   ├── loading.html
│   └── settings.html
├── src/
│   ├── backend/
│   │   ├── backend-mode.js
│   │   └── cloud-backend.js
│   ├── config/
│   │   ├── defaults.js
│   │   └── store.js
│   ├── ipc/
│   │   └── config-ipc.js
│   └── window/
│       └── main-window.js
├── entitlements/
│   ├── mac.plist
│   ├── mac.inherit.plist
│   ├── mas.plist
│   └── mas.inherit.plist
├── resources/
│   └── jre/                 # 可选：官网版内嵌 JRE
├── icons/
└── dist_electron/
```

## 快速开始

### 开发模式（local）

```bash
cd /path/to/social-sdk-parent
mvn -pl social-sdk-xianyu-manager package -DskipTests
cd scripts/electron
npm install
npm run start
```

### 官网版打包

```bash
cd scripts/electron
npm run build:mac
npm run build:win
```

官网版默认 `backendMode=local`：

1. Electron 启动内置 JAR
2. 后端监听 `127.0.0.1:{lastPort}`
3. Web UI 加载本地地址
4. Chrome/CDP/OpenList 等本地能力保留

如果要避免用户安装 Java，把 JRE 放入：

```text
scripts/electron/resources/jre/
```

macOS 期望：

```text
resources/jre/Contents/Home/bin/java
```

Windows 期望：

```text
resources/jre/bin/java.exe
```

### App Store 版打包

```bash
cd scripts/electron
ELECTRON_DISTRIBUTION=mas npm run build:mas
```

App Store 版强制使用 `backendMode=cloud`：

1. 不启动本地 JAR
2. 不启动本地 Chrome/CDP/OpenList
3. 只连接云端后台基础 URL
4. 使用 `entitlements/mas.plist` 与 `entitlements/mas.inherit.plist`

> 上架前需要替换真实 `embedded.provisionprofile`，并使用 Apple Distribution / Mac App Store 证书签名。

## 双模式配置项

配置保存在 `electron-store`：

| key | 说明 |
|---|---|
| `backendMode` | `local` 或 `cloud` |
| `cloudBaseUrl` | 云端后台基础 URL，例如 `https://api.example.com` |
| `lastPort` | local 模式监听端口，默认 8080 |
| `javaPath` | local 模式 Java 路径；若 resources/jre 存在则优先内嵌 JRE |
| `autoStart` | 开机自启 |
| `minimizeToTray` | 关闭窗口最小化到托盘（MAS 禁用） |
| `startInTray` | 启动时隐藏窗口 |

## 数据目录

| 平台 | 路径 |
|---|---|
| macOS | `~/Library/Application Support/xianyu-manager-desktop/` |
| Windows | `%APPDATA%/xianyu-manager-desktop/` |

local 模式会在该目录下创建：

```text
data/
logs/
uploads/
chrome-profiles/
config/
```

并向后端传入：

```text
-Duser.dir=<userData>
-DDB_PATH=<userData>/data/xianyu-manager.db
-Dlogging.file.path=<userData>/logs
```

## 分发建议

### 官网版

适合完整本地能力：

- Spring Boot 本地后端
- SQLite / 本地数据目录
- Chrome/CDP 指纹隔离
- OpenList / 本地文件能力
- 托盘常驻

需要：

- Developer ID 签名
- Notarization
- 可选内嵌 JRE
- Windows 代码签名

### App Store 版

适合云端 SaaS 客户端：

- 只连接云端后台
- 不启动本地后端
- 不控制本地 Chrome/CDP
- 不运行 OpenList 可执行文件
- 走 sandbox + network.client entitlement

需要：

- `mas` target
- App Sandbox entitlements
- Provisioning Profile
- App Store 隐私说明
- 云端多租户后台

## 静态校验

```bash
cd scripts/electron
npm run check
```

该命令会检查：

- `main.js`
- `preload.js`
- `src/**/*.js`

是否存在语法错误。
