# 触控精灵（TapSprite）— 自二进制恢复的可维护源码

> **说明**：本仓库 `android/` + `desktop/` 源码是从已发布二进制（App **0.9.59** / EXE **1.1.62**）**反编译整理 + 协议对照重写**而来，**不是**原作者完整工程快照。行为「大致能做」；缺口见下文。

官方发布物仍在 `public/`（历史 apk/exe、文档站、OCR 模型），**本 PR 不删除**这些安装包。

## 目录

| 路径 | 说明 |
|------|------|
| `android/` | Android Gradle 工程（`applicationId=com.tapsprite.agent`；version 见 `app/build.gradle` / `dist-channel.json`） |
| `desktop/` | Go 模块 PC 控制台（HTTP/UDP `0.0.0.0:18766`，内嵌 `web/ui.html` + **WebView2** 窗） |
| `public/` | 文档站 + 历史 apk/exe + `public/ocr/*.onnx` |
| `GUIDE.md` / `version.json` | 产品文档与版本清单 |
| `dist-channel.json` | 分支检新清单（PC/App 自更新；**不**覆盖 `public/version.json`） |
| `docs/TOOLCHAIN.md` | 跨平台工具链：git 同步什么、每台机器装什么、换机协议 |
| `scripts/setup-env.sh` | 检查 Go/JDK 17/SDK 34 并写入 gitignored `android/local.properties` |

换机器（Bot box ↔ web Grok Build 等）前先 **commit + push**，到达后先 **`git pull` 一次**。不要把 `android/local.properties` 拷到另一台机器。详见 `docs/TOOLCHAIN.md` 与 `AGENTS.md`「Environment / 跨平台」。

## 端口

- 手机本地控制台 HTTP：**18765**（`ConsoleServer`）
- 电脑 PC 控制台 HTTP + UDP 发现：**18766**（监听 **`0.0.0.0`**，手机可扫局域网 IP）

## PC 壳：进程内嵌 WebView2（1.1.73）

侧栏/status 只展示判定出的**唯一**局域网 IPv4（过滤虚拟网卡 + 默认路由出口 + 手机同 /24 优先）。App 手输 IP「连接」会强制上线并始终 hello。

自动更新 HTTP 客户端在 Windows 上优先读系统/IE 代理（`ProxyEnable`+`ProxyServer`），再环境变量，再探测本机常见代理端口；详见 `desktop/proxy_windows.go`。

Rebuild **1.1.73** 使用 [`github.com/jchv/go-webview2`](https://github.com/jchv/go-webview2) **进程内嵌** Microsoft Edge WebView2 窗口（Title=`触控精灵 v1.1.73`，1280×800），导航到 `http://127.0.0.1:18766/`，主线程 `Run()` 消息循环。

**硬禁止**（已删除）：`chrome.exe` / `msedge.exe` / `--app=` / `openBrowser` / `rundll32` 开页，以及「已用应用模式打开 …」这类日志。

**前提**：Windows 需已安装 **[Microsoft Edge WebView2 Runtime](https://developer.microsoft.com/microsoft-edge/webview2/)**（Win10/11 多数机器已自带）。交叉编译 **不需要 CGO**（`CGO_ENABLED=0`）。

主线程模型：HTTP `ListenAndServe` + UDP 在 goroutine；本机端口可连后再在主线程开 WebView。关窗或 `/api/quit` 结束进程。

## 安卓：编译与安装

从**仓库根目录**（相对路径）。每台机器自己的 SDK/JDK，不要把 `/workspace` 写进已提交文件。

1. 安装 **JDK 17** 与 Android SDK：**platform-34**、**build-tools 34.0.0**、platform-tools（compileSdk/targetSdk 34，minSdk 24）。
2. 配置 SDK 路径（二选一）：

```bash
# A) helper（推荐）：根据 ANDROID_HOME 或本机探测写入 gitignored local.properties
export JAVA_HOME=/path/to/jdk-17            # 本机路径
export ANDROID_HOME=/path/to/Android/Sdk    # 本机路径
./scripts/setup-env.sh
# 缺包时： ./scripts/setup-env.sh --install-sdk

# B) 手工
cp android/local.properties.example android/local.properties
# 编辑 sdk.dir=（正斜杠即可；Windows 例 C:/Users/you/AppData/Local/Android/Sdk）
```

3. 编译 APK：

```bash
# 仓库根目录
./android/gradlew -p android :app:assembleDebug
cp android/app/build/outputs/apk/debug/app-debug.apk android/dist/tapsprite0-9-xx.apk
```

仓内现成 debug 包：`android/dist/`（文件名跟 `dist-channel.json` 的 `apk_ver`）。

4. 依赖（已写在 `app/build.gradle`）：
   - `org.luaj:luaj-jse:3.0.1`
   - `com.microsoft.onnxruntime:onnxruntime-android:1.17.3`
5. OCR 模型已固化在 `android/app/src/main/assets/ocr/`。
6. **换签说明**：重建包使用 debug/新签名，**无法覆盖安装**原商店/原签名包；需先卸载旧包。

## PC：编译与运行

交叉编译 **不需要 CGO**，也不需要本机构建机安装 WebView2。

```bash
# 仓库根目录
cd desktop
go test ./...
# Windows 成品（Linux/macOS 可交叉）：
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-H windowsgui -s -w" -o dist/tapsprite1-1-xx.exe .
```

产物：`desktop/dist/tapsprite1-1-xx.exe`（**不会**覆盖 `public/` 下历史 exe）。目标 Windows 仍需已装 [WebView2 Runtime](https://developer.microsoft.com/microsoft-edge/webview2/)。

### 联机要点

- HTTP 绑定 **`0.0.0.0:18766`**，手机可连局域网 IP。
- UDP `:18766` 收到 `TSHELLO` **立刻**回 `TS?`。
- `/api/hello` → `{"ok":true}`；`/api/pull` 未知设备 `{"hello":true}`、无命令 `{"cmd":null}`、有命令则**扁平** JSON（含 `type`）。
- **不**再自动改防火墙（避免闪黑窗）。若手机连不上，请手动放行入站 TCP/UDP **18766**。

### 已实现的核心 API

- `/api/hello` `/api/bye` `/api/status` `/api/device` `/api/pull` `/api/notice` `/api/channel`
- `/api/script` `/api/control`（start/stop/shot）
- `/api/shot` `/api/frame` `/api/pixel` `/api/pushshot`（`rawz` / `png`）
- `/api/refresh` `/api/save` `/api/saveas` `/api/savescript` `/api/slot` `/api/undo` `/api/crop` `/api/rotate`
- UDP：`TSHELLO` → `TS?`；并向手机 `18765` 发 `TS?`

### 仍为 stub / 缺口（PC）

- 需本机已装 **WebView2 Runtime**（未装则无法出窗）
- `/api/crop` `/api/rotate` 已实现真裁剪/真旋转（undo 为截图栈）
- ``/api/selfupdate` `/api/fetchapk` `/api/updatestatus` `/api/apkstatus` `/api/apkfile` 已实现；托盘仍 TODO
- QOI 截图编码未实现（手机侧主要走 `rawz`/`png`）
- 抓抓边角 / 命令移植（P1）本轮不做
- 防火墙规则在无管理员权限时可能加不上
- 多设备槽位/UI 细节可能与 1.1.62 有差异

## 安卓完成度 / 缺口

**已有**：jadx 整理的自有 Java 类；Manifest/资源/assets；LuaJ + ONNX；debug APK 可编过。

**缺口 / 风险**：反编译边角、OCR 复杂场景、无原签名私钥、UI 纯代码构建。

## 测试步骤

1. PC 运行 `desktop/dist/` 下当前 `tapsprite1-1-xx.exe` → 应弹出 **内嵌 WebView2 独立窗**（不是 Chrome/Edge `--app=`，也不是普通浏览器标签）。
2. 若首次启动，确认已装 WebView2 Runtime；确认 Windows 防火墙允许 18766；侧栏状态栏会显示本机局域网 IP。
3. 手机装 `android/dist/` 下当前 `tapsprite0-9-xx.apk`，开无障碍等权限，打开「电脑联机」（可手动填 PC IP）。
4. 设备出现在 PC 下拉框后，下发脚本 / 点截图，确认画面刷新。
5. 联机失败：查防火墙、同一 WiFi、PC 日志里的 UDP/hello。

## 免责

本树为失源后的自用恢复工程，请勿声称「已完全恢复原版源码」。
