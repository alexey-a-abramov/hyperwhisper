#!/data/data/com.termux/files/usr/bin/bash
# Build libllama.so for arm64-v8a using Termux's on-device clang.
#
# Mirrors build-whisper-so.sh but compiles llama.cpp + a fresh copy of ggml
# (kept in cpp/llama-ggml/ so it stays decoupled from whisper's older
# vendored ggml in cpp/ggml/). Output goes to
# app/src/main/jniLibs/arm64-v8a/libllama.so so AGP packs it into the APK.
#
# CPU-only build (NEON + dotprod + fp16). Vulkan is added in a follow-up
# pass once glslc is available in Termux.

set -euo pipefail

cd "$(dirname "$0")/.."

CPP_SRC=src/main/cpp
OUT_DIR=src/main/jniLibs/arm64-v8a
BUILD_DIR=build/llama-so/arm64-v8a

mkdir -p "$OUT_DIR" "$BUILD_DIR"

LLAMA_SRC=$CPP_SRC/llama-src
LLAMA_INC=$CPP_SRC/llama-include
GGML_DIR=$CPP_SRC/llama-ggml

INCLUDES=(
    -I"$LLAMA_INC"
    -I"$LLAMA_SRC"
    -I"$GGML_DIR/include"
    -I"$GGML_DIR/src"
    -I"$GGML_DIR/src/ggml-cpu"
)

# -DGGML_USE_DOTPROD / FP16 mirror the CMake ARM path. They turn on
# the vectorised dot-product / fp16 kernels that match -march=armv8.2-a+...
COMMON_DEFS=(
    -DGGML_USE_CPU
    -DGGML_USE_DOTPROD
    -DGGML_USE_FP16_VECTOR_ARITHMETIC
    -DNDEBUG
    -D_GNU_SOURCE
    # CMake normally injects these from ggml/CMakeLists.txt; we set them
    # explicitly so ggml_version() / ggml_commit() compile.
    "-DGGML_VERSION=\"hyperwhisper-vendored\""
    "-DGGML_COMMIT=\"unknown\""
)

CFLAGS=(
    -fPIC
    -O3
    -march=armv8.2-a+fp16+dotprod
    "${COMMON_DEFS[@]}"
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -Wno-unused-function
    -Wno-unused-variable
    -Wno-unused-but-set-variable
    "${INCLUDES[@]}"
)

CXXFLAGS=(-std=c++17 "${CFLAGS[@]}")
C_FLAGS=(-std=c11 "${CFLAGS[@]}")

# ---- ggml core (CPU-only, no DL backends) ----
GGML_C=(
    "$GGML_DIR/src/ggml.c"
    "$GGML_DIR/src/ggml-alloc.c"
    "$GGML_DIR/src/ggml-quants.c"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu.c"
    "$GGML_DIR/src/ggml-cpu/quants.c"
    "$GGML_DIR/src/ggml-cpu/arch/arm/quants.c"
)

GGML_CXX=(
    "$GGML_DIR/src/ggml.cpp"
    "$GGML_DIR/src/ggml-backend.cpp"
    "$GGML_DIR/src/ggml-backend-reg.cpp"
    "$GGML_DIR/src/ggml-threading.cpp"
    "$GGML_DIR/src/ggml-opt.cpp"
    "$GGML_DIR/src/gguf.cpp"
    "$GGML_DIR/src/ggml-cpu/ggml-cpu.cpp"
    "$GGML_DIR/src/ggml-cpu/binary-ops.cpp"
    "$GGML_DIR/src/ggml-cpu/unary-ops.cpp"
    "$GGML_DIR/src/ggml-cpu/vec.cpp"
    "$GGML_DIR/src/ggml-cpu/ops.cpp"
    "$GGML_DIR/src/ggml-cpu/traits.cpp"
    "$GGML_DIR/src/ggml-cpu/repack.cpp"
    "$GGML_DIR/src/ggml-cpu/hbm.cpp"
    "$GGML_DIR/src/ggml-cpu/arch/arm/repack.cpp"
)

# ---- llama core (everything except quant/saver — inference only) ----
LLAMA_CXX=(
    "$LLAMA_SRC/llama.cpp"
    "$LLAMA_SRC/llama-adapter.cpp"
    "$LLAMA_SRC/llama-arch.cpp"
    "$LLAMA_SRC/llama-batch.cpp"
    "$LLAMA_SRC/llama-chat.cpp"
    "$LLAMA_SRC/llama-context.cpp"
    "$LLAMA_SRC/llama-cparams.cpp"
    "$LLAMA_SRC/llama-grammar.cpp"
    "$LLAMA_SRC/llama-graph.cpp"
    "$LLAMA_SRC/llama-hparams.cpp"
    "$LLAMA_SRC/llama-impl.cpp"
    "$LLAMA_SRC/llama-io.cpp"
    "$LLAMA_SRC/llama-kv-cache.cpp"
    "$LLAMA_SRC/llama-kv-cache-iswa.cpp"
    "$LLAMA_SRC/llama-memory.cpp"
    "$LLAMA_SRC/llama-memory-hybrid.cpp"
    "$LLAMA_SRC/llama-memory-hybrid-iswa.cpp"
    "$LLAMA_SRC/llama-memory-recurrent.cpp"
    "$LLAMA_SRC/llama-mmap.cpp"
    "$LLAMA_SRC/llama-model.cpp"
    "$LLAMA_SRC/llama-model-loader.cpp"
    "$LLAMA_SRC/llama-model-saver.cpp"
    "$LLAMA_SRC/llama-quant.cpp"
    "$LLAMA_SRC/llama-sampler.cpp"
    "$LLAMA_SRC/llama-vocab.cpp"
    "$LLAMA_SRC/unicode.cpp"
    "$LLAMA_SRC/unicode-data.cpp"
)

# llama-src/models/*.cpp — every model architecture llama.cpp supports.
# Included en bloc; pruning would force ongoing maintenance every time
# llama.cpp adds a new arch and we'd hit "model arch not supported".
mapfile -t MODELS < <(ls "$LLAMA_SRC"/models/*.cpp 2>/dev/null)

# ---- our JNI bridge ----
JNI_CXX=(
    "$CPP_SRC/llama-jni.cpp"
)

OBJECTS=()

compile_c() {
    local src="$1"
    local obj="$BUILD_DIR/$(echo "${src#$CPP_SRC/}" | sed 's|/|_|g').o"
    echo "  cc  $src"
    clang "${C_FLAGS[@]}" -c "$src" -o "$obj"
    OBJECTS+=("$obj")
}

compile_cxx() {
    local src="$1"
    local obj="$BUILD_DIR/$(echo "${src#$CPP_SRC/}" | sed 's|/|_|g').o"
    echo "  c++ $src"
    clang++ "${CXXFLAGS[@]}" -c "$src" -o "$obj"
    OBJECTS+=("$obj")
}

for src in "${GGML_C[@]}";   do compile_c   "$src"; done
for src in "${GGML_CXX[@]}"; do compile_cxx "$src"; done
for src in "${LLAMA_CXX[@]}"; do compile_cxx "$src"; done
for src in "${MODELS[@]}";    do compile_cxx "$src"; done
for src in "${JNI_CXX[@]}";  do compile_cxx "$src"; done

echo "  link libllama.so"
clang++ -shared \
    -Wl,--gc-sections \
    -Wl,--exclude-libs,ALL \
    -Wl,-soname,libllama.so \
    -o "$OUT_DIR/libllama.so" \
    "${OBJECTS[@]}" \
    -llog -lc++_shared

# libc++_shared.so should already have been bundled by build-whisper-so.sh,
# but copy it again here so each script is independently runnable.
TERMUX_LIB=/data/data/com.termux/files/usr/lib
if [ -f "$TERMUX_LIB/libc++_shared.so" ] && [ ! -f "$OUT_DIR/libc++_shared.so" ]; then
    cp "$TERMUX_LIB/libc++_shared.so" "$OUT_DIR/libc++_shared.so"
fi

if command -v patchelf >/dev/null 2>&1; then
    patchelf --remove-rpath "$OUT_DIR/libllama.so" || true
fi

ls -lh "$OUT_DIR/libllama.so"
echo "OK"
