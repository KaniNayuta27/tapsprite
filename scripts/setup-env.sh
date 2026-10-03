#!/usr/bin/env bash
# TapSprite toolchain helper (idempotent).
# Checks Go / JDK 17 / Android SDK and writes gitignored android/local.properties
# from ANDROID_HOME (or a probed SDK). Never commits machine paths.
#
# Usage:
#   ./scripts/setup-env.sh              # check + write local.properties
#   ./scripts/setup-env.sh --install-sdk  # also fetch cmdline-tools + platform-34
#   ./scripts/setup-env.sh --print-env
#   ./scripts/setup-env.sh --help
set -euo pipefail

GO_MIN="1.22"
GO_PIN="1.24.4"
JDK_MAJOR="17"
ANDROID_API="34"
BUILD_TOOLS="34.0.0"
CMDLINE_TOOLS_VER="11076708"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_DIR="${REPO_ROOT}/android"
LOCAL_PROPS="${ANDROID_DIR}/local.properties"
ENV_FILE="${REPO_ROOT}/.env.toolchain"

INSTALL_SDK=0
PRINT_ENV=0
FAIL=0

usage() {
  cat <<EOF
TapSprite setup-env.sh — check toolchain and write android/local.properties

  --install-sdk   If platform-34 / build-tools ${BUILD_TOOLS} are missing, download
                  Android cmdline-tools ${CMDLINE_TOOLS_VER} into ANDROID_HOME
                  (default: \$HOME/Android/Sdk; never writes host paths into git).
  --print-env     Print export JAVA_HOME / ANDROID_HOME lines.
  --help          This text.

Pins: Go >= ${GO_MIN} (prefer ${GO_PIN}), JDK ${JDK_MAJOR},
      platforms;android-${ANDROID_API}, build-tools;${BUILD_TOOLS}, platform-tools.

Does not write absolute paths into any git-tracked file.
EOF
}

log()  { printf 'setup-env: %s\n' "$*"; }
warn() { printf 'setup-env: WARN: %s\n' "$*" >&2; }
err()  { printf 'setup-env: ERROR: %s\n' "$*" >&2; FAIL=1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --install-sdk) INSTALL_SDK=1 ;;
    --print-env)   PRINT_ENV=1 ;;
    -h|--help)     usage; exit 0 ;;
    *) err "unknown flag: $1"; usage; exit 2 ;;
  esac
  shift
done

uname_s="$(uname -s 2>/dev/null || echo unknown)"

is_windows() {
  case "$uname_s" in
    MINGW*|MSYS*|CYGWIN*|Windows_NT) return 0 ;;
    *) return 1 ;;
  esac
}

# Git Bash /c/Users/foo -> C:/Users/foo
unix_to_gradle_path() {
  local p="$1"
  if is_windows && [[ "$p" =~ ^/([a-zA-Z])/(.*)$ ]]; then
    local drive="${BASH_REMATCH[1]}"
    local rest="${BASH_REMATCH[2]}"
    drive="$(printf '%s' "$drive" | tr 'a-z' 'A-Z')"
    p="${drive}:/${rest}"
  fi
  p="${p//\\//}"
  printf '%s' "$p"
}

# Java properties: escape \ and :
to_sdk_dir_prop() {
  local p
  p="$(unix_to_gradle_path "$1")"
  p="${p//\\/\\\\}"
  p="${p//:/\\:}"
  printf '%s' "$p"
}

java_major() {
  local home="$1"
  local bin="${home}/bin/java"
  [ -x "$bin" ] || return 1
  local line
  line="$("$bin" -version 2>&1 | head -n 1 || true)"
  # openjdk version "17.0.20"  /  java version "1.8.0_xxx"
  if [[ "$line" =~ version\ \"1\.([0-9]+) ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  elif [[ "$line" =~ version\ \"([0-9]+) ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  else
    return 1
  fi
}

go_version_str() {
  local g="$1"
  "$g" version 2>/dev/null | awk '{print $3}' | sed 's/^go//'
}

# Compare dotted versions: 0 if $1 >= $2
ver_ge() {
  local a="$1" b="$2"
  # strip leftover suffix (linux/amd64 etc.)
  a="${a%% *}"
  a="$(printf '%s' "$a" | tr -cd '0-9.')"
  b="$(printf '%s' "$b" | tr -cd '0-9.')"
  [ -n "$a" ] && [ -n "$b" ] || return 1
  local IFS=.
  # shellcheck disable=SC2086
  set -- $a
  local a1="${1:-0}" a2="${2:-0}" a3="${3:-0}"
  # shellcheck disable=SC2086
  set -- $b
  local b1="${1:-0}" b2="${2:-0}" b3="${3:-0}"
  [ "$a1" -gt "$b1" ] && return 0
  [ "$a1" -lt "$b1" ] && return 1
  [ "$a2" -gt "$b2" ] && return 0
  [ "$a2" -lt "$b2" ] && return 1
  [ "$a3" -ge "$b3" ]
}

# --- Go ---
GO_BIN=""
if command -v go >/dev/null 2>&1; then
  GO_BIN="$(command -v go)"
elif [ -x /usr/local/go/bin/go ]; then
  GO_BIN=/usr/local/go/bin/go
fi

GO_VER=""
if [ -n "$GO_BIN" ]; then
  GO_VER="$(go_version_str "$GO_BIN")"
  if ver_ge "$GO_VER" "$GO_MIN"; then
    log "Go go${GO_VER} at ${GO_BIN} (min ${GO_MIN}, pin ${GO_PIN})"
    case "$GO_VER" in
      1.24.*) ;;
      *) warn "Go ${GO_VER} works (>= ${GO_MIN}) but pin is ${GO_PIN} if you can install it." ;;
    esac
  else
    err "Go ${GO_VER} is below min ${GO_MIN}. Install ${GO_PIN} from https://go.dev/dl/"
  fi
else
  err "Go not found. Install ${GO_PIN} (min ${GO_MIN}) and put it on PATH."
fi

# --- JDK 17 ---
JDK_CANDIDATES=()
[ -n "${JAVA_HOME:-}" ] && JDK_CANDIDATES+=("$JAVA_HOME")
if command -v java >/dev/null 2>&1; then
  java_bin="$(command -v java)"
  java_bin_dir="$(cd "$(dirname "$java_bin")/.." && pwd)"
  JDK_CANDIDATES+=("$java_bin_dir")
fi
JDK_CANDIDATES+=(
  /opt/java/openjdk
  /usr/lib/jvm/java-17-openjdk-amd64
  /usr/lib/jvm/java-17-openjdk
  /usr/lib/jvm/temurin-17
  /usr/lib/jvm/temurin-17-jdk-amd64
  /opt/homebrew/opt/openjdk@17
  /usr/local/opt/openjdk@17
)

FOUND_JAVA_HOME=""
for cand in "${JDK_CANDIDATES[@]}"; do
  [ -n "$cand" ] || continue
  maj="$(java_major "$cand" || true)"
  if [ "$maj" = "$JDK_MAJOR" ]; then
    FOUND_JAVA_HOME="$cand"
    break
  fi
done

if [ -n "$FOUND_JAVA_HOME" ]; then
  export JAVA_HOME="$FOUND_JAVA_HOME"
  log "JDK ${JDK_MAJOR} at JAVA_HOME=${JAVA_HOME}"
else
  err "JDK ${JDK_MAJOR} not found. Set JAVA_HOME to a JDK ${JDK_MAJOR} install (app/build.gradle uses JavaVersion.VERSION_17)."
fi

# --- Android SDK ---
SDK_CANDIDATES=()
[ -n "${ANDROID_HOME:-}" ] && SDK_CANDIDATES+=("$ANDROID_HOME")
[ -n "${ANDROID_SDK_ROOT:-}" ] && SDK_CANDIDATES+=("$ANDROID_SDK_ROOT")
if [ -f "$LOCAL_PROPS" ]; then
  existing="$(grep -E '^sdk\.dir=' "$LOCAL_PROPS" | tail -n 1 | cut -d= -f2- | sed 's/\\:/:/g; s/\\\\/\\/g' || true)"
  [ -n "$existing" ] && SDK_CANDIDATES+=("$existing")
fi
SDK_CANDIDATES+=(
  "${HOME}/Android/Sdk"
  "${HOME}/Library/Android/sdk"
  "${HOME}/android-sdk"
  /opt/android-sdk
  /usr/lib/android-sdk
)
[ -n "${LOCALAPPDATA:-}" ] && SDK_CANDIDATES+=("${LOCALAPPDATA}/Android/Sdk")

sdk_looks_ok() {
  local root="$1"
  [ -d "${root}/platforms/android-${ANDROID_API}" ] \
    && [ -d "${root}/build-tools/${BUILD_TOOLS}" ]
}

FOUND_SDK=""
for cand in "${SDK_CANDIDATES[@]}"; do
  [ -n "$cand" ] || continue
  if [ -d "$cand" ]; then
    FOUND_SDK="$cand"
    break
  fi
done

cmdline_url() {
  local os
  case "$uname_s" in
    Darwin) os=mac ;;
    MINGW*|MSYS*|CYGWIN*|Windows_NT) os=win ;;
    *) os=linux ;;
  esac
  printf 'https://dl.google.com/android/repository/commandlinetools-%s-%s_latest.zip' "$os" "$CMDLINE_TOOLS_VER"
}

accept_licenses() {
  local root="$1"
  mkdir -p "${root}/licenses"
  # Well-known hashes; keeps --install-sdk non-interactive.
  printf '24333f8a63b6825ea9c5514f83c2829b004d1fee\n' > "${root}/licenses/android-sdk-license"
  printf '84831b9409646161a2814e2d3e08c1f8e65e9265\n' > "${root}/licenses/android-sdk-preview-license"
}

find_sdkmanager() {
  local root="$1"
  local c
  for c in \
      "${root}/cmdline-tools/latest/bin/sdkmanager" \
      "${root}/cmdline-tools/bin/sdkmanager" \
      "${root}/tools/bin/sdkmanager"; do
    if [ -x "$c" ] || [ -f "$c" ]; then
      printf '%s' "$c"
      return 0
    fi
  done
  return 1
}

install_sdk() {
  local root="$1"
  mkdir -p "$root"
  accept_licenses "$root"

  local sm
  sm="$(find_sdkmanager "$root" || true)"
  if [ -z "$sm" ]; then
    log "Downloading Android cmdline-tools ${CMDLINE_TOOLS_VER} into ${root}"
    if ! command -v unzip >/dev/null 2>&1; then
      err "unzip is required to install cmdline-tools"
      return 1
    fi
    local zip tmp
    tmp="$(mktemp -d)"
    zip="${tmp}/cmdline-tools.zip"
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 -o "$zip" "$(cmdline_url)"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$zip" "$(cmdline_url)"
    else
      err "curl or wget required to download cmdline-tools"
      rm -rf "$tmp"
      return 1
    fi
    unzip -q "$zip" -d "$tmp"
    mkdir -p "${root}/cmdline-tools"
    rm -rf "${root}/cmdline-tools/latest"
    if [ -d "${tmp}/cmdline-tools/bin" ]; then
      mv "${tmp}/cmdline-tools" "${root}/cmdline-tools/latest"
    elif [ -d "${tmp}/tools/bin" ]; then
      mv "${tmp}/tools" "${root}/cmdline-tools/latest"
    else
      err "unexpected cmdline-tools zip layout"
      rm -rf "$tmp"
      return 1
    fi
    rm -rf "$tmp"
    sm="$(find_sdkmanager "$root")"
  fi

  if [ -z "${JAVA_HOME:-}" ]; then
    err "JAVA_HOME required to run sdkmanager"
    return 1
  fi
  log "sdkmanager: platform-tools platforms;android-${ANDROID_API} build-tools;${BUILD_TOOLS}"
  "$sm" --sdk_root="$root" \
    "platform-tools" \
    "platforms;android-${ANDROID_API}" \
    "build-tools;${BUILD_TOOLS}"
}

if [ -z "$FOUND_SDK" ]; then
  if [ "$INSTALL_SDK" -eq 1 ]; then
    FOUND_SDK="${ANDROID_HOME:-${HOME}/Android/Sdk}"
    log "No SDK found; installing into ${FOUND_SDK}"
    install_sdk "$FOUND_SDK" || err "SDK install failed"
  else
    err "Android SDK not found. Set ANDROID_HOME, or copy android/local.properties.example → android/local.properties, or re-run with --install-sdk."
  fi
else
  if ! sdk_looks_ok "$FOUND_SDK"; then
    if [ "$INSTALL_SDK" -eq 1 ]; then
      log "SDK at ${FOUND_SDK} missing platform-${ANDROID_API} or build-tools ${BUILD_TOOLS}; installing"
      install_sdk "$FOUND_SDK" || err "SDK package install failed"
    else
      err "SDK at ${FOUND_SDK} is missing platforms/android-${ANDROID_API} and/or build-tools/${BUILD_TOOLS}. Re-run with --install-sdk (or install those packages yourself)."
    fi
  else
    log "Android SDK at ${FOUND_SDK} (android-${ANDROID_API}, build-tools ${BUILD_TOOLS})"
  fi
fi

if [ -n "$FOUND_SDK" ] && sdk_looks_ok "$FOUND_SDK"; then
  export ANDROID_HOME="$FOUND_SDK"
  export ANDROID_SDK_ROOT="$FOUND_SDK"
  if [ -d "${FOUND_SDK}/platform-tools" ]; then
    log "platform-tools present"
  else
    warn "platform-tools directory missing (adb). --install-sdk will add it."
  fi
fi

# --- write gitignored local.properties ---
if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
  mkdir -p "$ANDROID_DIR"
  prop="$(to_sdk_dir_prop "$ANDROID_HOME")"
  cat > "$LOCAL_PROPS" <<EOF
## Generated by scripts/setup-env.sh — gitignored, do not commit.
## Machine-local SDK path; regenerate on each host.
sdk.dir=${prop}
EOF
  log "wrote ${LOCAL_PROPS} (gitignored)"
  if command -v git >/dev/null 2>&1; then
    if git -C "$REPO_ROOT" check-ignore -q android/local.properties 2>/dev/null; then
      :
    else
      warn "android/local.properties is not gitignored — will not add it; fix .gitignore"
    fi
  fi
fi

# --- optional machine-local env file ---
if [ -n "${JAVA_HOME:-}" ] || [ -n "${ANDROID_HOME:-}" ]; then
  {
    echo "# Generated by scripts/setup-env.sh — gitignored, do not commit."
    [ -n "${JAVA_HOME:-}" ] && printf 'export JAVA_HOME=%q\n' "$JAVA_HOME"
    [ -n "${ANDROID_HOME:-}" ] && printf 'export ANDROID_HOME=%q\n' "$ANDROID_HOME"
    [ -n "${ANDROID_SDK_ROOT:-}" ] && printf 'export ANDROID_SDK_ROOT=%q\n' "$ANDROID_SDK_ROOT"
    echo 'export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}${ANDROID_HOME:+$ANDROID_HOME/platform-tools:}$PATH"'
  } > "$ENV_FILE"
  log "wrote ${ENV_FILE} (gitignored). Source:  . ./.env.toolchain"
fi

if [ "$PRINT_ENV" -eq 1 ]; then
  [ -n "${JAVA_HOME:-}" ] && printf 'export JAVA_HOME=%q\n' "$JAVA_HOME"
  [ -n "${ANDROID_HOME:-}" ] && printf 'export ANDROID_HOME=%q\n' "$ANDROID_HOME"
  [ -n "${ANDROID_SDK_ROOT:-}" ] && printf 'export ANDROID_SDK_ROOT=%q\n' "$ANDROID_SDK_ROOT"
fi

echo
log "repo-root relative builds (after sourcing .env.toolchain):"
log "  APK:  ./android/gradlew -p android :app:assembleDebug"
log "  EXE:  ( cd desktop && GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags='-H windowsgui -s -w' -o dist/tapsprite1-1-xx.exe . )"
log "See docs/TOOLCHAIN.md"

exit "$FAIL"
