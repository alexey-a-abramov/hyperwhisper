# whisper.cpp + ggml CPU build for HyperWhisper.
# Adapted from whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt
# (MIT). Vendored sources sit beside this file.

cmake_minimum_required(VERSION 3.22.1)
project(hyperwhisper)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(WHISPER_SRC ${CMAKE_SOURCE_DIR}/whisper-src)
set(WHISPER_INC ${CMAKE_SOURCE_DIR}/whisper-include)
set(GGML_DIR    ${CMAKE_SOURCE_DIR}/ggml)

set(SOURCE_FILES
    ${WHISPER_SRC}/whisper.cpp
    ${CMAKE_SOURCE_DIR}/jni.c

    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml-alloc.c
    ${GGML_DIR}/src/ggml-backend.cpp
    ${GGML_DIR}/src/ggml-backend-reg.cpp
    ${GGML_DIR}/src/ggml-quants.c
    ${GGML_DIR}/src/ggml-threading.cpp
    ${GGML_DIR}/src/ggml-opt.cpp
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu-aarch64.cpp
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu-hbm.cpp
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu-quants.c
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu-traits.cpp
)

find_library(LOG_LIB log)

add_library(whisper SHARED ${SOURCE_FILES})

target_include_directories(whisper PRIVATE
    ${WHISPER_INC}
    ${WHISPER_SRC}
    ${GGML_DIR}/include
    ${GGML_DIR}/src
    ${GGML_DIR}/src/ggml-cpu
)

# arm64-v8a only on first pass — see the advisor's note. Multi-ABI is a
# follow-up. Enable fp16 + dotprod where available.
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisper PRIVATE -march=armv8.2-a+fp16+dotprod)
endif()

target_compile_definitions(whisper PRIVATE
    GGML_USE_CPU
)

# Release flags only — debug builds keep symbols for lldb.
if(NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(whisper PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper PRIVATE -ffunction-sections -fdata-sections)
    target_link_options(whisper PRIVATE -Wl,--gc-sections)
endif()

target_link_libraries(whisper ${LOG_LIB} android)
