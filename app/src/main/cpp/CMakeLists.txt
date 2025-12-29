cmake_minimum_required(VERSION 3.22.1)
project(hyperwhisper_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Disable shared library builds for whisper.cpp
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

# Add whisper.cpp library
add_subdirectory(whisper)

# Include directories
include_directories(
    ${CMAKE_SOURCE_DIR}/whisper
    ${CMAKE_SOURCE_DIR}/whisper/include
    ${CMAKE_SOURCE_DIR}/whisper/src
)

# Create JNI wrapper library
add_library(hyperwhisper_jni SHARED
    whisper_jni.cpp
    audio_converter.cpp
)

# Link whisper library and Android libraries
target_link_libraries(hyperwhisper_jni
    whisper
    log
    android
)

# Compiler flags for optimization
target_compile_options(hyperwhisper_jni PRIVATE
    -O3
    -ffast-math
    -funroll-loops
    -Wall
    -Wextra
)

# ARM-specific optimizations
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(hyperwhisper_jni PRIVATE
        -march=armv8-a
    )
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(hyperwhisper_jni PRIVATE
        -march=armv7-a
        -mfpu=neon
    )
endif()
