#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEPS=${CZ_ANDROID_DEPS:-"$ROOT/thirdparty/android"}
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK 27.2.12479018}"
TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
PREFIX="$DEPS/ffmpeg-install"
BUILD="$DEPS/ffmpeg-build"
mkdir -p "$BUILD"
cd "$BUILD"
"$DEPS/ffmpeg/configure" \
    --prefix="$PREFIX" --target-os=android --arch=aarch64 --enable-cross-compile \
    --cc="$TC/aarch64-linux-android29-clang" --cxx="$TC/aarch64-linux-android29-clang++" \
    --ar="$TC/llvm-ar" --nm="$TC/llvm-nm" --ranlib="$TC/llvm-ranlib" --strip="$TC/llvm-strip" \
    --enable-shared --disable-static --enable-pic \
    --disable-everything --disable-autodetect --disable-programs --disable-doc \
    --disable-avdevice --disable-avformat --disable-avfilter --disable-swscale --disable-swresample \
    --disable-network --disable-iconv --enable-decoder=xma1,xma2 \
    --disable-gpl --disable-nonfree --disable-version3 \
    --extra-ldflags="-Wl,-z,max-page-size=16384"
# Check the result, not just the requested flags. The APK must stay LGPL-compatible.
grep -q '^#define CONFIG_GPL 0$' config.h
grep -q '^#define CONFIG_NONFREE 0$' config.h
grep -q '^#define CONFIG_VERSION3 0$' config.h
make -j"${CZ_JOBS:-2}"
make install
