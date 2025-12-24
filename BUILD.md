# HyperWhisper Build Guide

## Quick Start

### Build Debug APK
```bash
./build.sh
```
or
```bash
./build.sh debug
```

### Build Release APK
```bash
./build.sh release
```

### Build and Install to Device
```bash
./build.sh install
```

### Clean Build
```bash
./build.sh clean
```

## Build Script Features

✅ Auto-increments version code on every build
✅ Color-coded output
✅ Shows APK location and size
✅ Works with or without Gradle wrapper

## Manual Build Commands

If you prefer using gradle directly:

### Debug Build
```bash
gradle assembleDebug
```

### Release Build
```bash
gradle assembleRelease
```

### Install to Device
```bash
gradle installDebug
```

### Clean
```bash
gradle clean
```

## APK Output Locations

After building, find your APKs here:

**Debug:**
```
app/build/outputs/apk/debug/app-debug.apk
```

**Release:**
```
app/build/outputs/apk/release/app-release.apk
```

## Version Management

Versions are managed in `gradle.properties`:

```properties
VERSION_NAME=1.0      # User-facing version (manual)
VERSION_CODE=1        # Build number (auto-increments)
```

### How Auto-Increment Works

Every time you run a build:
1. Build script reads current VERSION_CODE from gradle.properties
2. Increments it by 1
3. Saves the new value
4. Builds with the new version code

Example:
```
Build 1: VERSION_CODE=1  → app shows "Version 1.0 (Code 1)"
Build 2: VERSION_CODE=2  → app shows "Version 1.0 (Code 2)"
Build 3: VERSION_CODE=3  → app shows "Version 1.0 (Code 3)"
```

### Updating Version Name

When releasing a new version, manually edit `gradle.properties`:

```properties
VERSION_NAME=1.1      # Change this for new releases
VERSION_CODE=50       # Keep current or reset to 1
```

## Setting up Gradle Wrapper (Optional)

If you want to use `./gradlew` instead of system gradle:

```bash
./setup-wrapper.sh
```

This creates:
- `gradlew` - Unix/Linux wrapper script
- `gradlew.bat` - Windows wrapper script
- `gradle/wrapper/` - Wrapper JAR and properties

## Troubleshooting

### "gradle not found"
Install gradle:
```bash
pkg install gradle
```

### "Permission denied"
Make scripts executable:
```bash
chmod +x build.sh setup-wrapper.sh
```

### Clean build if issues occur
```bash
./build.sh clean
./build.sh debug
```

### View detailed build logs
```bash
gradle assembleDebug --stacktrace
```

## Build Types

### Debug
- Used for development and testing
- Includes debugging symbols
- Not optimized
- Larger APK size
- Can be installed alongside release builds

### Release
- Used for production
- Optimized and obfuscated (if configured)
- Smaller APK size
- Requires signing for Play Store

## Next Steps

1. **First time?** Run `./build.sh` to create your first build
2. **Need wrapper?** Run `./setup-wrapper.sh`
3. **Ready to test?** Run `./build.sh install` to install on device
4. **Making changes?** Version code increments automatically on each build

## Quick Commands Reference

```bash
# Build commands
./build.sh              # Build debug
./build.sh debug        # Build debug
./build.sh release      # Build release
./build.sh install      # Build and install debug
./build.sh clean        # Clean build

# Setup
./setup-wrapper.sh      # Create gradle wrapper
chmod +x *.sh           # Make scripts executable

# Manual gradle
gradle assembleDebug    # Build debug
gradle assembleRelease  # Build release
gradle installDebug     # Install debug to device
gradle clean            # Clean build

# Find APKs
find app/build -name "*.apk"

# Install APK manually
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Build Configuration Files

- `build.sh` - Main build script
- `setup-wrapper.sh` - Gradle wrapper setup
- `gradle.properties` - Version configuration
- `app/build.gradle.kts` - App build configuration
- `build.gradle.kts` - Project build configuration

---

Happy building! 🚀
