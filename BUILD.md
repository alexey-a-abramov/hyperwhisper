# HyperWhisper Build Guide

## Quick Start

### Build on Android/Termux (Recommended for Cloud flavor)

```bash
# Cloud-only build (no native code, ~40 minutes)
./build-android.sh cloud

# Local build (with whisper.cpp, may require NDK setup)
./build-android.sh local

# Build both flavors
./build-android.sh both
```

The script automatically:
- ✅ Configures ARM64 AAPT2 for Android
- ✅ Builds the APK(s)
- ✅ Restores gradle.properties (keeps cloud builds working)
- ✅ Shows APK location and size

### Build in GitHub Actions Cloud (Recommended for Local flavor)

```bash
# Trigger cloud build (both flavors)
gh workflow run "Build APKs" --field build_local=true --field build_cloud=true

# Wait and download when ready (~7 minutes)
./download-cloud-build.sh
```

Downloaded APKs will be in: `cloud-builds/latest/`

---

## Build Flavors

| Flavor | Native Code | Best Built | Build Time | APK Size |
|--------|-------------|------------|------------|----------|
| **cloud** | ❌ None | Android | ~40 min | 83 MB |
| **local** | ✅ whisper.cpp | Cloud | ~7 min | 83 MB |

## Build Scripts

- **`build-android.sh`** - Automated Android/Termux builds
- **`download-cloud-build.sh`** - Download GitHub Actions builds

## Latest Features (v1.0 build 47+)

- ✨ Explicit Cloud/Local toggle in settings
- ✨ Prominent hybrid processing card
- ✨ Cloud provider selector (11 providers)
