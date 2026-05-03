#!/data/data/com.termux/files/usr/bin/bash
# Build libwhisper.so for arm64-v8a using Termux's on-device clang.
#
# Why this exists: Google ships the Android NDK only for x86_64 hosts, so on
# an aarch64 device the SDK-installed clang can't run. Termux clang IS the
# same NDK r29 toolchain, but built natively for aarch64 Android — see
# `clang --version` (it reports "built by NDK r29 (14206865)"). It targets
# /system/bin/linker64 + Bionic libc, which is what Android apps need.
#
# Output goes to app/src/main/jniLibs/arm64-v8a/libwhisper.so so AGP packs
# it into the APK automatically.

set -euo pipefail

cd "$(dirname "$0")/.."

CPP_SRC=src/main/cpp
OUT_DIR=src/main/jniLibs/arm64-v8a
BUILD_DIR=build/whisper-so/arm64-v8a

mkdir -p "$OUT_DIR" "$BUILD_DIR"

WHISPER_SRC=$CPP_SRC/whisper-src
WHISPER_INC=$CPP_SRC/whisper-include
GGML_DIR=$CPP_SRC/ggml

INCLUDES=(
    -I"$WHISPER_INC"
    -I"$WHISPER_SRC"
    -I"$GGML_DIR/include"
    -I"$GGML_DIR/src"
    -I"$GGML_DIR/src/ggml-cpu"
)

CFLAGS=(
    -fPIC
    -O3
    -march=armv8.2-a+fp16+dotprod
    -DGGML_USE_CPU
    -DNDEBUG
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    "${INCLUDES[@]}"
)

CXXFLAGS=(-std=c++17 "${CFLAGS[@]}")
C_FLAGS=(-std=c11 "${CFLAGS[@]}")

C_SOURCES=(
    "$GGML_DIR/src/ggml.c"
    "$GGML_DIR/src/ggml-alloc.c"
    "$GGML_DIR/src/ggml-quants.c"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu.c"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu-quants.c"
    "$CPP_SRC/jni.c"
)

CXX_SOURCES=(
    "$WHISPER_SRC/whisper.cpp"
    "$GGML_DIR/src/ggml-backend.cpp"
    "$GGML_DIR/src/ggml-backend-reg.cpp"
    "$GGML_DIR/src/ggml-threading.cpp"
    "$GGML_DIR/src/ggml-opt.cpp"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu.cpp"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu-aarch64.cpp"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu-hbm.cpp"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu-traits.cpp"
)

OBJECTS=()

for src in "${C_SOURCES[@]}"; do
    obj="$BUILD_DIR/$(echo "${src#$CPP_SRC/}" | sed 's|/|_|g').o"
    echo "  cc  $src"
    clang "${C_FLAGS[@]}" -c "$src" -o "$obj"
    OBJECTS+=("$obj")
done

for src in "${CXX_SOURCES[@]}"; do
    obj="$BUILD_DIR/$(echo "${src#$CPP_SRC/}" | sed 's|/|_|g').o"
    echo "  c++ $src"
    clang++ "${CXXFLAGS[@]}" -c "$src" -o "$obj"
    OBJECTS+=("$obj")
done

echo "  link libwhisper.so"
clang++ -shared \
    -Wl,--gc-sections \
    -Wl,--exclude-libs,ALL \
    -Wl,-soname,libwhisper.so \
    -o "$OUT_DIR/libwhisper.so" \
    "${OBJECTS[@]}" \
    -llog -lc++_shared

# Bundle libc++_shared.so alongside — Android NDK r21+ requires the app to
# provide it, and the default linker run path won't include Termux's lib dir.
TERMUX_LIB=/data/data/com.termux/files/usr/lib
if [ -f "$TERMUX_LIB/libc++_shared.so" ]; then
    cp "$TERMUX_LIB/libc++_shared.so" "$OUT_DIR/libc++_shared.so"
fi

# Strip Termux RUNPATH — Android's loader can find libc.so/libdl.so on its own
# via /system/lib64/. The embedded RUNPATH points at Termux paths that other
# apps can't access.
if command -v patchelf >/dev/null 2>&1; then
    patchelf --remove-rpath "$OUT_DIR/libwhisper.so" || true
    patchelf --remove-rpath "$OUT_DIR/libc++_shared.so" || true
fi

ls -lh "$OUT_DIR/libwhisper.so" "$OUT_DIR/libc++_shared.so" 2>/dev/null
echo "OK"
