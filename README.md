# 触控精灵（TapSprite）— 自二进制恢复的可维护源码

> **说明**：本仓库 `android/` + `desktop/` 源码是从已发布二进制（App **0.9.59** / EXE **1.1.62**）**反编译整理 + 协议对照重写**而来，**不是**原作者完整工程快照。行为「大致能做」；缺口见下文。

官方发布物仍在 `public/`（历史 apk/exe、文档站、OCR 模型），**本 PR 不删除**这些安装包。

## 目录

| 路径 | 说明 |
|------|------|
| `android/` | Android Gradle 工程（`applicationId=com.tapsprite.agent`，`0.9.59-rebuild` / versionCode **84**） |
| `desktop/` | Go 模块 PC 控制台（HTTP/UDP `:18766`，内嵌 `web/ui.html`） |
| `public/` | 文档站 + 历史 apk/exe + `public/ocr/*.onnx` |
| `GUIDE.md` / `version.json` | 产品文档与版本清单 |

## 端口

- 手机本地控制台 HTTP：**18765**（`ConsoleServer`）
- 电脑 PC 控制台 HTTP + UDP 发现：**18766**

## 安卓：编译与安装

1. 安装 **Android Studio**（或 cmdline-tools），SDK **34**，JDK **17**。
2. 打开目录 `android/`（或用命令行）：

```bash
cd android
# 可选：cp local.properties.example local.properties 并填写 sdk.dir=
./gradlew :app:assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

3. 依赖（已写在 `app/build.gradle`）：
   - `org.luaj:luaj-jse:3.0.1`
   - `com.microsoft.onnxruntime:onnxruntime-android:1.17.3`
4. OCR 模型已固化在 `android/app/src/main/assets/ocr/`（`det.onnx` / `rec.onnx` / `ppocr_keys_v1.txt`），与 `public/ocr/` 同源。
5. **换签说明**：重建包使用 debug/新签名，**无法覆盖安装**原商店/原签名包；需先卸载旧包，或改 `applicationId`（会破坏无障碍/联机习惯）。

本 CI/沙箱已能 `assembleDebug` 通过；你本地用 Android Studio Sync 后安装即可。

## PC：编译与运行

```bash
cd desktop
go build -o dist/tapsprite .
# Windows 成品（已在本环境交叉编译过一份）：
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-H windowsgui -s -w" -o dist/tapsprite-1-1-62-rebuild.exe .
```

运行后浏览器打开 `http://127.0.0.1:18766`。Monaco 编辑器走 **unpkg CDN**（需联网）；`luaparse.js` 已本地 embed。

**注意**：`desktop/dist/*-rebuild.exe` **不会**静默覆盖 `public/tapsprite1-1-62.exe`。若要发布新包，请用新文件名并更新 `version.json`。

### 已实现的核心 API

- `/api/hello` `/api/bye` `/api/status` `/api/device` `/api/pull` `/api/notice`
- `/api/script` `/api/control`（start/stop/shot）
- `/api/shot` `/api/frame` `/api/pixel` `/api/pushshot`（支持手机 `rawz`=zlib RGB / `png`）
- `/api/refresh` `/api/save` `/api/saveas` `/api/savescript` `/api/slot` `/api/undo`
- UDP：收 `TSHELLO` 并回 `TS?`；并向手机 `18765` 发 `TS?`

### 仍为 stub / 缺口（PC）

- `/api/crop` `/api/rotate` 细节与原版裁剪栈不完全一致（undo 仅简单截图栈）
- `/api/selfupdate` `/api/fetchapk` / 电脑代下 APK、防火墙放行、托盘等：**TODO**
- QOI 截图编码未实现（手机侧主要走 `rawz`/`png`）
- 多设备槽位/UI 细节可能与 1.1.62 有差异

## 安卓完成度 / 缺口

**已有**：jadx 整理的 30 个自有 Java 类进仓；Manifest/资源/assets/脚本；LuaJ + ONNX；Mqtt 误引用已改成字面量 `"/"` / `"#"`；debug APK 可编过。

**缺口 / 风险**：

- 部分方法经反编译修复（`LanLink`/`Updater`/`OcrEngine` 等），逻辑与原版可能有边角差异
- OCR 连通域/检测路径仍含反编译痕迹，复杂场景建议回归
- 无原签名私钥；更新链路依赖新签名策略
- UI 仍为纯代码构建（几乎无 layout XML）

## 用户本地建议试一下

1. 手机装 `app-debug.apk`，开无障碍 + 悬浮窗 + 截屏权限，打开「电脑联机」。
2. PC 运行 rebuild exe 或 `go run .`，同一局域网看设备是否出现在下拉框。
3. 下发一小段脚本 / 点截图，确认 `/api/pushshot` → 画面刷新。
4. 若联机失败：查防火墙 18766、手机手动填 PC IP、两端日志。

## 免责

本树为失源后的自用恢复工程，请勿声称「已完全恢复原版源码」。
