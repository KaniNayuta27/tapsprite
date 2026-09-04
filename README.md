# 触控精灵（TapSprite）— 自二进制恢复的可维护源码

> **说明**：本仓库 `android/` + `desktop/` 源码是从已发布二进制（App **0.9.59** / EXE **1.1.62**）**反编译整理 + 协议对照重写**而来，**不是**原作者完整工程快照。行为「大致能做」；缺口见下文。

官方发布物仍在 `public/`（历史 apk/exe、文档站、OCR 模型），**本 PR 不删除**这些安装包。

## 目录

| 路径 | 说明 |
|------|------|
| `android/` | Android Gradle 工程（`applicationId=com.tapsprite.agent`，`0.9.59-rebuild` / versionCode **84**） |
| `desktop/` | Go 模块 PC 控制台（HTTP/UDP `0.0.0.0:18766`，内嵌 `web/ui.html`） |
| `public/` | 文档站 + 历史 apk/exe + `public/ocr/*.onnx` |
| `GUIDE.md` / `version.json` | 产品文档与版本清单 |

## 端口

- 手机本地控制台 HTTP：**18765**（`ConsoleServer`）
- 电脑 PC 控制台 HTTP + UDP 发现：**18766**（监听 **`0.0.0.0`**，手机可扫局域网 IP）

## 独立窗原理（对齐正式 1.1.62）

正式包 **不是** 内嵌 WebView2 控件，而是启动本机 **Edge / Chrome** 的应用模式窗：

```text
msedge.exe|chrome.exe --app=http://127.0.0.1:18766 --window-size=1280,800
```

Rebuild **1.1.63-rebuild** 同样走这条路径（`openAppWindow`），**禁止**用系统默认浏览器随便开标签页。

**前提**：Windows 需已安装 Microsoft Edge 或 Google Chrome。找不到浏览器时进程仍继续 Listen，日志会提示手动打开上述 URL。

主进程仍是 `windowsgui` HTTP 服务；应用窗只是 UI 壳。

## 安卓：编译与安装

1. 安装 **Android Studio**（或 cmdline-tools），SDK **34**，JDK **17**。
2. 打开目录 `android/`（或用命令行）：

```bash
cd android
# 可选：cp local.properties.example local.properties 并填写 sdk.dir=
./gradlew :app:assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

仓内现成 debug 包：`android/dist/tapsprite0-9-59-rebuild-debug.apk`（本轮未改 Java，版本保持）。

3. 依赖（已写在 `app/build.gradle`）：
   - `org.luaj:luaj-jse:3.0.1`
   - `com.microsoft.onnxruntime:onnxruntime-android:1.17.3`
4. OCR 模型已固化在 `android/app/src/main/assets/ocr/`。
5. **换签说明**：重建包使用 debug/新签名，**无法覆盖安装**原商店/原签名包；需先卸载旧包。

## PC：编译与运行

```bash
cd desktop
go test ./...
go build -o dist/tapsprite .
# Windows 成品：
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-H windowsgui -s -w" -o dist/tapsprite-1-1-63-rebuild.exe .
```

产物：`desktop/dist/tapsprite-1-1-63-rebuild.exe`（**不会**覆盖 `public/tapsprite1-1-62.exe`）。

### 联机要点

- HTTP 绑定 **`0.0.0.0:18766`**，手机可连局域网 IP。
- UDP `:18766` 收到 `TSHELLO` **立刻**回 `TS?`。
- `/api/hello` → `{"ok":true}`；`/api/pull` 未知设备 `{"hello":true}`、无命令 `{"cmd":null}`、有命令则**扁平** JSON（含 `type`）。
- 启动时尽量用 `netsh advfirewall` 放行 TCP/UDP **18766**（失败只记日志，需管理员权限时请手动放行）。

### 已实现的核心 API

- `/api/hello` `/api/bye` `/api/status` `/api/device` `/api/pull` `/api/notice`
- `/api/script` `/api/control`（start/stop/shot）
- `/api/shot` `/api/frame` `/api/pixel` `/api/pushshot`（`rawz` / `png`）
- `/api/refresh` `/api/save` `/api/saveas` `/api/savescript` `/api/slot` `/api/undo`
- UDP：`TSHELLO` → `TS?`；并向手机 `18765` 发 `TS?`

### 仍为 stub / 缺口（PC）

- `/api/crop` `/api/rotate` 与原版裁剪栈不完全一致（undo 仅简单截图栈）
- `/api/selfupdate` `/api/fetchapk` / 托盘等：**TODO**
- QOI 截图编码未实现（手机侧主要走 `rawz`/`png`）
- 无 Edge/Chrome 时没有真正的独立窗（需安装浏览器）
- 防火墙规则在无管理员权限时可能加不上
- 多设备槽位/UI 细节可能与 1.1.62 有差异

## 安卓完成度 / 缺口

**已有**：jadx 整理的自有 Java 类；Manifest/资源/assets；LuaJ + ONNX；debug APK 可编过。

**缺口 / 风险**：反编译边角、OCR 复杂场景、无原签名私钥、UI 纯代码构建。

## 测试步骤

1. PC 运行 `tapsprite-1-1-63-rebuild.exe` → 应弹出 **Edge/Chrome 应用窗**（不是普通浏览器标签）。
2. 若首次启动，确认 Windows 防火墙允许 18766；侧栏状态栏会显示本机局域网 IP。
3. 手机装 `android/dist/tapsprite0-9-59-rebuild-debug.apk`，开无障碍等权限，打开「电脑联机」（可手动填 PC IP）。
4. 设备出现在 PC 下拉框后，下发脚本 / 点截图，确认画面刷新。
5. 联机失败：查防火墙、同一 WiFi、PC 日志里的 UDP/hello。

## 免责

本树为失源后的自用恢复工程，请勿声称「已完全恢复原版源码」。
