# Quick Build Guide

Simple command-line build instructions for HyperWhisper.

## Build from Command Line

### Option 1: Simple Debug Build (Recommended)

```bash
# Build debug APK (fastest)
./build.sh debug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Android/Termux Build

```bash
# Auto-configures AAPT2 for ARM64
./build-android.sh

# Output: builds/app-debug.apk
```

### Option 3: Using Gradle Directly

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Build and install to connected device
./gradlew installDebug
```

## All Build Commands

| Command | Description | Output Location |
|---------|-------------|-----------------|
| `./build.sh` | Build debug APK (default) | `app/build/outputs/apk/debug/` |
| `./build.sh release` | Build release APK | `app/build/outputs/apk/release/` |
| `./build.sh install` | Build & install to device | Installed on device |
| `./build.sh clean` | Clean build artifacts | - |
| `./build-android.sh` | Termux-optimized build | `builds/app-debug.apk` |

## Installation

```bash
# Via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or copy to device storage
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
```

## Requirements

- JDK 17 or higher
- Android SDK
- For Termux: `pkg install aapt2 openjdk-17`

## Typical Build Time

- Debug build: ~30-60 seconds
- Release build: ~1-2 minutes

For detailed information, see [BUILD.md](BUILD.md)
