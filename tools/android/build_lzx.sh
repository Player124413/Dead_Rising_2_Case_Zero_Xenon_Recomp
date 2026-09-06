#!/usr/bin/env bash
# Rebuild the replaceable LGPL LZX library without the game or the recompiler.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEPS=${CZ_ANDROID_DEPS:-"$ROOT/thirdparty/android"}
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK 27.2.12479018}"
SRC="$DEPS/xenon/thirdparty/libmspack/libmspack/mspack"
OUT="$DEPS/lgpl-rebuild"
mkdir -p "$OUT"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -O2 -fPIC -shared "$SRC/lzxd.c" -I"$SRC" \
    -Wl,--no-undefined -Wl,-soname,libcz_lzx.so -Wl,-z,max-page-size=16384 \
    -o "$OUT/libcz_lzx.so"
echo "Rebuilt $OUT/libcz_lzx.so; see docs/android-lgpl.md for replacement instructions."
