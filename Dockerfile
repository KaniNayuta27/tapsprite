# TapSprite toolchain image — JDK 17 + Go 1.24.4 + Android SDK 34
# Does not assume /workspace. Mount the repo at /src.
#
#   docker build -t tapsprite-toolchain .
#   docker run --rm -v "$PWD":/src -w /src tapsprite-toolchain \
#     bash -lc './scripts/setup-env.sh && ./android/gradlew -p android :app:assembleDebug'
FROM eclipse-temurin:17-jdk-jammy

ARG GO_VERSION=1.24.4
ARG ANDROID_COMPILE_SDK=34
ARG ANDROID_BUILD_TOOLS=34.0.0
ARG CMDLINE_TOOLS=11076708

ENV DEBIAN_FRONTEND=noninteractive \
    GOPATH=/opt/gopath \
    GOROOT=/usr/local/go \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH=/usr/local/go/bin:/opt/gopath/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git unzip wget \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz" \
    | tar -C /usr/local -xz \
    && go version

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" /tmp/cmdline \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" \
         -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline \
    && mv /tmp/cmdline/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline /tmp/cmdline-tools.zip \
    && mkdir -p "${ANDROID_HOME}/licenses" \
    && printf '24333f8a63b6825ea9c5514f83c2829b004d1fee\n' > "${ANDROID_HOME}/licenses/android-sdk-license" \
    && printf '84831b9409646161a2814e2d3e08c1f8e65e9265\n' > "${ANDROID_HOME}/licenses/android-sdk-preview-license" \
    && sdkmanager --sdk_root="${ANDROID_HOME}" \
         "platform-tools" \
         "platforms;android-${ANDROID_COMPILE_SDK}" \
         "build-tools;${ANDROID_BUILD_TOOLS}"

WORKDIR /src
# Image is toolchain-only; clone/mount the repo. setup-env.sh writes gitignored local.properties.
CMD ["bash"]
