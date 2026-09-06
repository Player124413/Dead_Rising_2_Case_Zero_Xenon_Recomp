#!/usr/bin/env bash
# DXC must RUN on the phone. Desktop libdxcompiler.so is not an Android library.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEPS=${CZ_ANDROID_DEPS:-"$ROOT/thirdparty/android"}
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the pinned NDK}"
SRC="$DEPS/dxc"
HOST="$DEPS/dxc-host"
BUILD="$DEPS/dxc-build"
COMMON=(-G Ninja -DCMAKE_BUILD_TYPE=Release -C "$SRC/cmake/caches/PredefinedParams.cmake"
    -DLLVM_INCLUDE_TESTS=OFF -DHLSL_INCLUDE_TESTS=OFF -DSPIRV_BUILD_TESTS=OFF
    -DHLSL_BUILD_DXILCONV=OFF -DLLVM_ENABLE_ZLIB=OFF -DLLVM_ENABLE_TERMINFO=OFF
    -DLLVM_INCLUDE_EXAMPLES=OFF -DLLVM_INCLUDE_DOCS=OFF -DENABLE_SPIRV_CODEGEN=ON)
cmake -S "$SRC" -B "$HOST" "${COMMON[@]}" -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++
cmake --build "$HOST" --target llvm-tblgen clang-tblgen -j"${CZ_JOBS:-2}"
cmake -S "$SRC" -B "$BUILD" "${COMMON[@]}" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
    -DLLVM_NATIVE_BUILD="$HOST" -DLLVM_TABLEGEN="$HOST/bin/llvm-tblgen" -DCLANG_TABLEGEN="$HOST/bin/clang-tblgen" \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON
cmake --build "$BUILD" --target dxcompiler -j"${CZ_JOBS:-2}"
