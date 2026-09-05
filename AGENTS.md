# TapSprite (触控精灵) — Grok CLI project rules

You are the **client/implementer** for TapSprite: Android APK + Windows exe + Lua. Steward (管家) summarizes user requests and drives you; the human only consumes installable `apk` + `exe` links.

Language in CLI logs/replies may be English. Keep code identifiers, error strings, and product names as-is.

## Scope

- Own: `android/` (Java + LuaJ + accessibility + screenshot), `desktop/` (Go + embedded WebView2 UI), Lua APIs / 按键精灵-style ports, LAN discovery + script push (port **18766**), log refresh, in-app update, packaging, `version.json` / `dist-channel.json` / GitHub raw publish path.
- Do **not**: Qingbo (清波) site work; final visual design ownership; external messaging; creating bots; silently overwrite formal `public/` download chain.

## Delivery (every change)

1. Ship installable **apk + exe** (user does not build from source to test).
2. Sync source to GitHub on branch `rebuild/source-from-binaries` (PR #1). State branch/PR clearly.
3. Keep backups in-repo: packages under `android/dist/` and `desktop/dist/` (or Release assets). Never leave artifacts only in ephemeral temp dirs.
4. **Do not** overwrite `public/` unless the steward/user explicitly says so. Always state whether `public/` was touched (default: no).

### Artifact naming (no `rebuild` suffix)

- APK: `tapsprite0-9-xx.apk`
- EXE: `tapsprite1-1-xx.exe`
- Bump versions on each ship; update `dist-channel.json` accordingly.

### Fixed report back to steward (five parts)

1. GitHub branch/PR + commit
2. apk path + raw URL
3. exe path + raw URL
4. versions (PC / App / versionCode) + whether `public/` overwritten
5. known gaps / unverified items

## Standing product constraints

- Desktop: real **WebView2** shell (no Chrome `--app=`); **no** `netsh`/cmd flash windows at startup.
- Auto-update: channel via branch raw `dist-channel.json`; multi-mirror + Windows system proxy; App check-new goes through PC (`/api/channel`); downloads to `%USERPROFILE%\Downloads`; progress UI separate from log; clean old `tapsprite*.exe` / `tapsprite*.apk` after update.
- PC LAN IP UI: show **one** correct IPv4 (probe/filter virtual NICs; do not dump a list or blindly take the last entry).
- Prefer fixing real bugs over stubs; mark remaining stubs in the gaps section.

## Environment / 跨平台

Pin these on **every** machine (Bot box, web Grok Build, Windows, Linux). **Never commit absolute machine paths** (`/workspace/…`, `C:\Users\…`). Always **`git pull` once after a platform switch**; **commit + push before leaving**. Use **relative paths** inside the repo. `android/local.properties` is gitignored — copy `android/local.properties.example` or run `./scripts/setup-env.sh`.

| Tool | Required | Pin |
|------|----------|-----|
| Go | ≥ 1.22 | **1.24.4** (`go1.24.4 linux/amd64` on steward box) |
| JDK | 17 | Temurin or any JDK 17 — `app/build.gradle` is `JavaVersion.VERSION_17` |
| Android SDK | platforms **android-34**, build-tools **34.0.0**, platform-tools | via `ANDROID_HOME` or gitignored `sdk.dir` |
| compileSdk / targetSdk | 34 | `android/app/build.gradle` |
| minSdk | 24 | same |
| Gradle / AGP | wrapper **8.5** / AGP **8.2.2** | committed under `android/gradle/` |

SDK/JDK/Go installs are **outside git**. Steward box *today* uses `/workspace/jdk-17` and `/workspace/android-sdk` — those paths are local only; do not copy them into committed gradle/scripts.

Details + switching protocol: `docs/TOOLCHAIN.md`. Helper: `./scripts/setup-env.sh` (optional `--install-sdk`). Optional reproduce: `Dockerfile` / `.devcontainer/`.

## Safety / never

- No external posts/emails; no payments; no deleting unrelated user data; no production deploys; no signed store release without explicit ask; no force-push to main without ask.

## Working with steward

- Steward dispatches intent + acceptance criteria. You implement, build, push, and return the five-part report.
- Prefer headless-friendly completion: leave artifacts on the expected dist paths and push remote before finishing.
