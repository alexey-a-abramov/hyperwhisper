# Pre-built Native Libraries

This directory contains pre-built native libraries (.so files) for whisper.cpp JNI bindings.

## Why Pre-built Libraries?

Building native C++ code on Termux is not supported because the Android SDK's CMake and NDK tools are incompatible with Termux's Android environment.

## Options to Get Native Libraries

### Option 1: Use GitHub Actions (Recommended)
The `.github/workflows/build-native.yml` workflow will automatically:
1. Build native libraries when C++ code changes
2. Commit the built .so files to this directory
3. Make them available for Termux builds

**To trigger the build:**
- Push changes to the `master` branch, OR
- Go to Actions tab on GitHub and manually run the "Build Native Library" workflow

### Option 2: Build on Desktop/Laptop
If you have a Linux/Mac/Windows machine:

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/hyperwhisper.git
cd hyperwhisper

# Build the native library
./gradlew assembleLocalDebug

# The .so files will be in:
# app/build/intermediates/merged_native_libs/localDebug/out/lib/

# Copy them to jniLibs:
mkdir -p app/src/main/jniLibs
cp -r app/build/intermediates/merged_native_libs/localDebug/out/lib/* app/src/main/jniLibs/

# Commit and push
git add app/src/main/jniLibs
git commit -m "Add pre-built native libraries"
git push
```

### Option 3: Download from GitHub Actions Artifacts
After GitHub Actions builds successfully:
1. Go to the Actions tab in your repository
2. Click on the latest "Build Native Library" workflow run
3. Download the "native-libs" artifact
4. Extract and place the .so files in the appropriate directories here:
   - `arm64-v8a/libhyperwhisper_jni.so`
   - `armeabi-v7a/libhyperwhisper_jni.so`

## Expected Directory Structure

```
jniLibs/
├── arm64-v8a/
│   └── libhyperwhisper_jni.so
├── armeabi-v7a/
│   └── libhyperwhisper_jni.so
└── README.md (this file)
```

## Verification

Once the libraries are in place, the build system will:
- Skip CMake compilation (faster builds on Termux)
- Use the pre-built .so files directly
- Load them via `System.loadLibrary("hyperwhisper_jni")`

## Troubleshooting

If you get `UnsatisfiedLinkError`:
1. Verify .so files exist in the correct directories
2. Check file permissions: `ls -la jniLibs/*/*.so`
3. Ensure ABI matches your device (use `adb shell getprop ro.product.cpu.abi`)
4. Clean and rebuild: `./gradlew clean assembleLocalDebug`
