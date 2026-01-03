# HyperWhisper Build Guide

## Quick Start (TL;DR)

### Option A: Build Locally on Termux/Android

```bash
# Clone and enter directory
cd /data/data/com.termux/files/home/projects/hyperwhisper

# Build cloud flavor (fastest, ~30 seconds)
./build-android.sh cloud

# Build local flavor (with native whisper.cpp, ~1 minute)
./build-android.sh local

# Build both flavors
./build-android.sh both
```

**Output**: APKs in `builds/local/` and `builds/cloud/`

---

### Option B: Build via GitHub Actions

```bash
# Trigger build
gh workflow run build-apks.yml

# Wait ~5-7 minutes, then download
# Visit: https://github.com/YOUR_USERNAME/hyperwhisper/actions
```

---

## Build Flavors

| Flavor | Description | Native Code | Best Built On | APK Size |
|--------|-------------|-------------|---------------|----------|
| **cloud** | API-only voice processing | ❌ None | Termux, GitHub | ~15-43 MB |
| **local** | On-device whisper.cpp | ✅ whisper.cpp | GitHub | ~89-110 MB |

---

## Detailed Build Guide

### Architecture Overview

HyperWhisper uses a **hybrid build system** designed to work across different environments:

```
┌─────────────────────────────────────────────────────────────────┐
│                         Build Decision Tree                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Pre-built native libs exist in app/src/main/jniLibs/?       │
│     ├── YES → Skip CMake, use pre-built .so files              │
│     └── NO  → Check if whisper submodule exists                │
│         ├── YES → Run CMake (build native code)                 │
│         └── NO  → Skip native build entirely (cloud mode)       │
│                                                                  │
│  2. AAPT2 (Android Asset Packaging Tool)                        │
│     ├── Linux/x86_64 → Fails on ARM64 (Termux)                 │
│     └── Termux aapt2 → Works via gradle.properties override     │
│                                                                  │
│  3. NDK/CMake binaries                                           │
│     ├── Linux/x86_64 → Fails on ARM64 (Termux)                 │
│     └── Pre-built libs → No NDK needed!                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Environment-Specific Builds

### 📱 Building on Termux/Android (ARM64)

**Works for**: Both cloud and local flavors

**Requirements**:
```bash
pkg install aapt2 openjdk-17 -y
```

**Build**:
```bash
./build-android.sh cloud    # ~30 seconds
./build-android.sh local    # ~1 minute
```

**How it works**:
1. Script uncomments `android.aapt2FromMavenOverride` in gradle.properties
2. Points to Termux's ARM64-compatible aapt2 at `/data/data/com.termux/files/usr/bin/aapt2`
3. Pre-built native libs skip CMake entirely (no x86_64 NDK needed!)
4. Gradle builds APK using Termux toolchain

**Expected warnings** (harmless):
```
error: "llvm-objcopy" is for EM_X86_64 instead of EM_AARCH64
Unable to strip the following libraries, packaging them as they are
```
This just means debug symbols aren't removed (APK ~110MB vs 89MB).

---

### 🌐 Building via GitHub Actions

**Works for**: Both cloud and local flavors

**Trigger**:
```bash
# Via CLI
gh workflow run build-apks.yml

# Or visit: https://github.com/<user>/hyperwhisper/actions
# Click "Build APKs" → "Run workflow"
```

**How it works**:
1. **Cloud job**: Runs with `submodules: false` → no whisper.cpp → no CMake
2. **Local job**: Runs with `submodules: true` → downloads whisper.cpp → builds native code
3. NDK/CMake are x86_64 Linux binaries (work on GitHub runners)
4. APKs uploaded as artifacts

**Build times**:
- Cloud: ~3-4 minutes
- Local: ~5-7 minutes (whisper.cpp is large)

---

### 💻 Building on Desktop Linux

**Works for**: Both cloud and local flavors

**Requirements**:
- JDK 17+
- Android SDK (command-line tools)
- For local flavor: NDK 27.0.12077973 + CMake 3.22.1

**Build**:
```bash
./gradlew assembleCloudDebug
./gradlew assembleLocalDebug
```

---

## Common Pitfalls & Solutions

### ❌ Issue: AAPT2 Architecture Mismatch

```
error: "aapt2" is for EM_X86_64 instead of EM_AARCH64
```

**Cause**: Gradle downloads x86_64 AAPT2, but you're on ARM64 (Termux).

**Solution**: Use `build-android.sh` which configures Termux's aapt2:
```bash
./build-android.sh cloud
```

---

### ❌ Issue: CMake Architecture Mismatch

```
error: "cmake" has unexpected e_type: 2
```

**Cause**: NDK/CMake are x86_64, but you're on ARM64.

**Solution**: Ensure pre-built native libs exist:
```bash
ls app/src/main/jniLibs/*/*.so
# If empty, pull from repo or run GitHub Actions
git pull origin master  # Pre-built libs are in repo
```

---

### ❌ Issue: Cloud Flavor Fails with CMake Error

```
CMake Error at CMakeLists.txt:14 (add_subdirectory):
```

**Cause**: Workflow has `submodules: false`, but CMake is still configured.

**Solution**: Fixed in current build.gradle.kts - CMake only runs when:
- Pre-built libs don't exist AND
- Whisper submodule exists

---

### ❌ Issue: Native Libs Not Found in APK

**Cause**: `jniLibs` directory structure wrong or libs not included.

**Verify**:
```bash
ls -R app/src/main/jniLibs/
# Should show:
# arm64-v8a/libhyperwhisper_jni.so
# armeabi-v7a/libhyperwhisper_jni.so
```

---

## File Structure

```
hyperwhisper/
├── app/
│   ├── build.gradle.kts          # Build configuration
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt    # Native build config
│       │   └── whisper/          # Submodule (for local flavor)
│       └── jniLibs/              # Pre-built native libs
│           ├── arm64-v8a/
│           │   └── libhyperwhisper_jni.so  (12MB)
│           └── armeabi-v7a/
│               └── libhyperwhisper_jni.so  (11MB)
├── .github/workflows/
│   ├── build-apks.yml            # GitHub Actions: build APKs
│   └── build-native.yml          # GitHub Actions: build native libs
├── build-android.sh              # Termux build script
├── builds/                       # Output directory
│   ├── local/app-local-debug.apk
│   └── cloud/app-cloud-debug.apk
└── gradle.properties             # AAPT2 override config
```

---

## Native Library Build System

### How Pre-Built Libs Work

1. **GitHub Actions** (`build-native.yml`):
   - Builds whisper.cpp for arm64-v8a and armeabi-v7a
   - Commits `.so` files to `app/src/main/jniLibs/`
   - Triggered by push to master

2. **Local Build** checks in order:
   ```kotlin
   val hasPreBuiltLibs = file("src/main/jniLibs/arm64-v8a/libhyperwhisper_jni.so").exists()
   val hasWhisperSubmodule = file("src/main/cpp/whisper/CMakeLists.txt").exists()

   if (!hasPreBuiltLibs && hasWhisperSubmodule) {
       // Configure CMake to build from source
   }
   // Otherwise: use pre-built or skip native entirely
   ```

3. **Result**: Termux builds work without NDK/CMake!

---

## Gradle Configuration Details

### app/build.gradle.kts

```kotlin
// Product flavors
productFlavors {
    create("local") {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake { cppFlags += ... }  // Only compiler flags
        }
    }
    create("cloud") {
        // No native configuration
    }
}

// Conditional CMake (only when needed)
if (!hasPreBuiltLibs && hasWhisperSubmodule) {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

### gradle.properties (AAPT2 Override)

```properties
# Uncomment for Termux/ARM64 builds:
# android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

The `build-android.sh` script toggles this automatically.

---

## Signing Configuration

APKs are signed with a consistent keystore for reproducible builds:

```kotlin
signingConfigs {
    create("shared") {
        val keystorePath = System.getenv("KEYSTORE_FILE")
            ?: file("${System.getProperty("user.home")}/.android/debug.keystore").absolutePath
        // Falls back to debug keystore if no env var set
    }
}
```

**GitHub Actions**: Uses generated debug keystore

**Local builds**: Uses `~/.android/debug.keystore`

---

## Installation

```bash
# Via ADB
adb install builds/local/app-local-debug.apk

# Or on-device (Termux)
cp builds/local/app-local-debug.apk /sdcard/Download/
# Then install via file manager
```

---

## Troubleshooting Checklist

- [ ] AAPT2 error? → Use `build-android.sh`
- [ ] CMake error? → Ensure pre-built libs exist: `git pull`
- [ ] Cloud flavor fails? → Check build.gradle.kts has conditional CMake
- [ ] APK too large? → Local flavor includes 75MB of models (expected)
- [ ] Native code not working? → Verify `jniLibs` architecture matches device

---

## Performance Comparison

| Environment | Cloud Build | Local Build | Notes |
|-------------|-------------|-------------|-------|
| **Termux/ARM64** | ~30s | ~1m | Uses pre-built libs, Termux aapt2 |
| **GitHub Actions** | ~3m | ~6m | Full x86_64 toolchain available |
| **Desktop Linux** | ~1m | ~4m | Requires SDK/NDK setup |

---

## Summary

1. **Quick local builds**: Use `./build-android.sh <flavor>`
2. **Cloud builds**: Use `gh workflow run build-apks.yml`
3. **Native libs are pre-built** in the repo - no NDK needed for Termux
4. **AAPT2 override** handles ARM64 compatibility
5. **Both flavors build successfully** on all platforms
