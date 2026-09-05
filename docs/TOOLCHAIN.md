# TapSprite toolchain (cross-platform)

Same GitHub branch (`rebuild/source-from-binaries`, PR #1) must build on the steward Bot box, web Grok Build, Windows, or any Linux host. **Git does not ship JDK/SDK/Go.** Each machine installs tools and writes a **gitignored** SDK path.

## What git syncs vs what each machine installs

| In git (this repo) | Not in git — install per machine |
|--------------------|----------------------------------|
| `android/` Gradle sources + **wrapper** (`gradle-8.5`, AGP 8.2.2) | **JDK 17** (`JAVA_HOME`) |
| `desktop/` Go module (`go 1.22` in `go.mod`) | **Go ≥ 1.22**, pin **1.24.4** if possible |
| `android/local.properties.example` | `android/local.properties` (`sdk.dir=…`) |
| `scripts/setup-env.sh`, this doc, `Dockerfile`, `.devcontainer/` | Android SDK **platform-34**, **build-tools 34.0.0**, **platform-tools** |
| `android/dist/*.apk`, `desktop/dist/*.exe`, `dist-channel.json` | Gradle caches (`android/.gradle/`, `~/.gradle/`) |
| | WebView2 **Runtime** on the **target Windows PC** (not needed to cross-compile) |

**Never commit** absolute machine paths (`/workspace/…`, `C:\Users\…`, `/opt/…`).  
`android/local.properties` and `.env.toolchain` are gitignored.

Steward box *today* (for reference only — do not copy these paths into git):

- Go: `go1.24.4 linux/amd64`
- JDK: `/workspace/jdk-17` (Java 17) — `app/build.gradle` already `JavaVersion.VERSION_17`
- Android SDK: `/workspace/android-sdk` (not in git); `platforms/android-34`; `build-tools/34.0.0`

## Pins

| Item | Value |
|------|--------|
| Go | ≥ 1.22, pin **1.24.4** |
| JDK | **17** |
| compileSdk / targetSdk | **34** |
| minSdk | **24** |
| build-tools | **34.0.0** |
| Gradle wrapper | **8.5** (`android/gradle/wrapper/gradle-wrapper.properties`) |
| Android Gradle Plugin | **8.2.2** |

## Switching protocol (Bot box ↔ web Grok Build ↔ any host)

1. **Before leaving** a machine: `git add` / `commit` / `push` `rebuild/source-from-binaries`.
2. **After arriving** on the other machine: `git pull` **once** before any build. Do not compile a stale tree.
3. Do **not** copy `android/local.properties` or `.env.toolchain` across machines.
4. On a new host: `./scripts/setup-env.sh` (or copy the example file and set `sdk.dir` / `ANDROID_HOME`).
5. Relative paths only inside the repo (`android/…`, `desktop/dist/…`).

## One-time per machine

```bash
# from repo root
export JAVA_HOME=/path/to/jdk-17          # your machine
export ANDROID_HOME=/path/to/Android/Sdk  # your machine
./scripts/setup-env.sh                    # writes gitignored android/local.properties
# optional if SDK packages are missing:
./scripts/setup-env.sh --install-sdk
. ./.env.toolchain                        # JAVA_HOME / ANDROID_HOME for this shell
```

Manual alternative (no script):

```bash
cp android/local.properties.example android/local.properties
# edit sdk.dir= to this machine's SDK (forward slashes OK)
```

Windows (PowerShell / cmd) — brief:

- Install Go 1.24.x and Eclipse Temurin **17**.
- Android Studio default SDK: `%LOCALAPPDATA%\Android\Sdk`
- Copy `android\local.properties.example` → `android\local.properties`
- Set `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` (forward slashes; do not commit).
- Git Bash can run `./scripts/setup-env.sh` if `JAVA_HOME` / `ANDROID_HOME` are set.
- Native exe: `cd desktop && go build -ldflags="-H windowsgui -s -w" -o dist\tapsprite1-1-xx.exe .`
- Cross-compile from Linux/macOS does **not** need CGO or a Windows JDK.

## Build from repo root (relative)

```bash
. ./.env.toolchain   # if you ran setup-env.sh

# APK (debug, signed with the debug keystore)
./android/gradlew -p android :app:assembleDebug
cp android/app/build/outputs/apk/debug/app-debug.apk android/dist/tapsprite0-9-xx.apk

# Windows EXE (cross-compile; CGO off)
( cd desktop && GOOS=windows GOARCH=amd64 CGO_ENABLED=0 \
    go build -ldflags="-H windowsgui -s -w" -o dist/tapsprite1-1-xx.exe . )
```

Bump `versionName` / `versionCode` in `android/app/build.gradle`, `version` in `desktop/main.go` + `desktop/web/ui.html`, and `dist-channel.json` on every ship. Do not overwrite `public/` unless explicitly asked.

## Docker / devcontainer (optional)

```bash
docker build -t tapsprite-toolchain .
docker run --rm -v "$PWD":/src -w /src tapsprite-toolchain \
  bash -lc './scripts/setup-env.sh && ./android/gradlew -p android :app:assembleDebug'
```

Image pins JDK 17, Go 1.24.4, SDK 34 under **`/opt/android-sdk`** (not `/workspace`). VS Code/Grok: `.devcontainer/devcontainer.json`.

## Gaps

- Web Grok Build is documented here; a given run may not have executed on that host.
- The Windows exe still needs **WebView2 Runtime** on the target PC (Win10/11 usually already have it).
