#!/usr/bin/env bash
# Usage: build.sh diagnostic | build.sh game /absolute/path/to/generated/ppc
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"
MODE=${1:-diagnostic}
case "$MODE" in diagnostic|game) ;; *) echo "Usage: $0 diagnostic|game [ppc-dir]" >&2; exit 2;; esac
export CZ_ANDROID_DEPS=${CZ_ANDROID_DEPS:-"$ROOT/thirdparty/android"}
NDK_VERSION=$(python3 -c 'import json; print(json.load(open("tools/android/dependencies.json"))["ndk"])')
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-"${ANDROID_HOME:?Set ANDROID_HOME}/ndk/$NDK_VERSION"}
if ! grep -q "Pkg.Revision = $NDK_VERSION" "$ANDROID_NDK_HOME/source.properties"; then
    echo "Use the locked NDK $NDK_VERSION (not a host compiler or another NDK)" >&2; exit 1
fi
python3 tools/android/bootstrap.py --root "$CZ_ANDROID_DEPS" --dxc
tools/android/build_ffmpeg.sh
tools/android/build_dxc.sh
BUILD="$ROOT/build/android-$MODE"
if [[ "$MODE" == diagnostic ]]; then
    PPC="$BUILD/stub-ppc"
    python3 tools/gen_stub_ppc.py --out "$PPC" --xenon "$CZ_ANDROID_DEPS/xenon"
    STUB=ON
else
    : "${2:?A game build requires the path to your real generated ppc tree}"
    PPC=$(realpath "$2")
    STUB=OFF
fi
cmake -S runtime/android -B "$BUILD" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DCMAKE_BUILD_TYPE=Release \
    -DCZ_ANDROID_DEPS="$CZ_ANDROID_DEPS" -DCZ_PPC_DIR="$PPC" -DCZ_ALLOW_STUB_PPC="$STUB" \
    -DCZ_ANDROID_DXC_LIBRARY="$CZ_ANDROID_DEPS/dxc-build/lib/libdxcompiler.so"
cmake --build "$BUILD" -j"${CZ_JOBS:-2}"
python3 tools/android/package_inputs.py --mode "$MODE" --deps "$CZ_ANDROID_DEPS" --build "$BUILD"
echo "Native libraries ready. Next: cd android && ./gradlew assemble${MODE^}Debug"
