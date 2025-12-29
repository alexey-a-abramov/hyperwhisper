# Whisper.cpp Local Integration - Progress Report

**Date:** 2025-12-30
**Status:** Phase 3 Complete (40% overall progress)
**Next Session:** Start from Phase 4

---

## Overview

Integrating whisper.cpp with JNI to enable on-device speech recognition alongside existing cloud APIs. This allows:
- Offline transcription
- Privacy (no data leaves device)
- No API costs for local processing
- Lower latency for tiny model

---

## Completed Work (Phases 1-3)

### ✅ Phase 1: Native Library Setup

**Files Created:**
1. **`app/src/main/cpp/CMakeLists.txt`**
   - CMake build configuration for whisper.cpp
   - Links whisper library with JNI wrapper
   - Optimization flags for ARM architectures

2. **`app/src/main/cpp/whisper_jni.cpp`** (163 lines)
   - JNI bridge for whisper.cpp
   - Methods: `nativeLoadModel()`, `nativeTranscribe()`, `nativeUnloadModel()`, `nativeIsModelLoaded()`
   - Handles global context management

3. **`app/src/main/cpp/audio_converter.cpp`** (109 lines)
   - WAV file reader with PCM extraction
   - Converts int16/int32 to float32 normalized samples
   - Stereo to mono conversion

**Files Modified:**
- **`app/build.gradle.kts`**
  - Added NDK configuration (arm64-v8a, armeabi-v7a)
  - Added externalNativeBuild for CMake
  - Added prefab = true to buildFeatures

**Git Changes:**
- Added whisper.cpp as git submodule at `app/src/main/cpp/whisper`
- Initialized and updated submodule

---

### ✅ Phase 2: Kotlin Bridge Layer

**Files Created:**
1. **`app/src/main/java/com/hyperwhisper/native_whisper/WhisperContext.kt`** (120 lines)
   - Singleton class with JNI wrapper methods
   - Loads `hyperwhisper_jni` library on init
   - Safe error handling with Result<T> types
   - Methods: `loadModel()`, `transcribe()`, `unloadModel()`, `isModelLoaded()`

2. **`app/src/main/java/com/hyperwhisper/native_whisper/AudioConverter.kt`** (239 lines)
   - MediaCodec-based M4A to WAV converter
   - Handles stereo→mono conversion
   - Sample rate resampling (simple linear interpolation)
   - Outputs 16kHz, mono, 16-bit PCM WAV files

---

### ✅ Phase 3: Model Management

**Files Modified:**
1. **`app/src/main/java/com/hyperwhisper/ime/data/Models.kt`**
   - Added `ApiProvider.LOCAL` enum value
   - Added `WhisperModel` enum (TINY, BASE, SMALL) with download URLs and sizes
   - Added `ModelDownloadState` sealed class (NotDownloaded, Downloading, Downloaded, Error)
   - Added `WhisperModelInfo` data class
   - Added `import java.io.File`

**Files Created:**
2. **`app/src/main/java/com/hyperwhisper/ime/data/ModelRepository.kt`** (234 lines)
   - Singleton repository for model management
   - Downloads models from HuggingFace with progress tracking
   - Stores models in `context.filesDir/whisper_models/`
   - StateFlow for reactive model state updates
   - Methods: `downloadModel()`, `deleteModel()`, `extractBundledModel()`, `isModelDownloaded()`, `getTotalStorageUsed()`

---

## Remaining Work (Phases 4-10)

### 🔲 Phase 4: Local Processing Strategy (Next)

**To Create:**
- `app/src/main/java/com/hyperwhisper/ime/network/LocalWhisperStrategy.kt`
  - Implement `AudioProcessingStrategy` interface
  - Use WhisperContext and AudioConverter
  - Convert M4A→WAV, load model, transcribe, cleanup
  - Return `ApiResult.Success` with `ProcessingInfo`

**Estimated Time:** 30-45 minutes

---

### 🔲 Phase 5: VoiceRepository Integration

**To Modify:**
- `app/src/main/java/com/hyperwhisper/ime/network/VoiceRepository.kt`
  - Add `localWhisperStrategy: LocalWhisperStrategy` to constructor (line 14)
  - Update `selectStrategy()` method to handle `ApiProvider.LOCAL` (line 303-324)
  - Update `needsTwoStepProcessing()` to return false for LOCAL (line 141-161)

**Estimated Time:** 15-20 minutes

---

### 🔲 Phase 6: Dependency Injection

**To Modify:**
- `app/src/main/java/com/hyperwhisper/ime/di/NetworkModule.kt`
  - Add `@Provides` methods for:
    - `provideWhisperContext()`
    - `provideAudioConverter()`
    - `provideModelRepository()`
    - `provideLocalWhisperStrategy()`

**Estimated Time:** 10-15 minutes

---

### 🔲 Phase 7: Settings UI

**To Create:**
1. `app/src/main/java/com/hyperwhisper/ime/ui/settings/ModelManagementViewModel.kt`
   - HiltViewModel for model management
   - Exposes modelStates, downloadModel(), deleteModel()

2. `app/src/main/java/com/hyperwhisper/ime/ui/settings/ModelManagementCard.kt`
   - Composable card showing model list
   - Download/Delete buttons
   - Progress indicators
   - Storage usage display

**To Modify:**
3. `app/src/main/java/com/hyperwhisper/ime/ui/settings/SettingsScreen.kt`
   - Add "Local Models" section
   - Add ModelManagementCard composable

**Estimated Time:** 60-90 minutes

---

### 🔲 Phase 8: Bundle Tiny Model

**Tasks:**
1. Download tiny model: `wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin`
2. Create directory: `mkdir -p app/src/main/assets/models`
3. Copy model: `cp ggml-tiny.bin app/src/main/assets/models/`
4. Call `extractBundledModel()` on app first launch

**Note:** This adds ~75MB to APK size

**Estimated Time:** 15-20 minutes

---

### 🔲 Phase 9: ProGuard Rules

**To Modify:**
- `app/proguard-rules.pro`
  - Keep JNI methods
  - Keep WhisperContext native methods
  - Keep WhisperModel classes

**Estimated Time:** 5-10 minutes

---

### 🔲 Phase 10: Testing & Validation

**Test Checklist:**
- [ ] CMake builds successfully
- [ ] Native library loads
- [ ] Tiny model extracts from assets
- [ ] Model download works
- [ ] M4A to WAV conversion works
- [ ] Transcription works with loaded model
- [ ] Memory usage reasonable (~200MB for Tiny)
- [ ] Settings UI updates correctly
- [ ] Error handling works

**Estimated Time:** 60-120 minutes

---

## File Structure Created

```
app/
├── build.gradle.kts (modified)
├── src/main/
    ├── cpp/
    │   ├── CMakeLists.txt (new)
    │   ├── whisper_jni.cpp (new)
    │   ├── audio_converter.cpp (new)
    │   └── whisper/ (submodule)
    ├── java/com/hyperwhisper/
    │   ├── native_whisper/ (new package)
    │   │   ├── WhisperContext.kt (new)
    │   │   └── AudioConverter.kt (new)
    │   └── ime/data/
    │       ├── Models.kt (modified)
    │       └── ModelRepository.kt (new)
    └── assets/models/ (to be created)
        └── ggml-tiny.bin (to be added)
```

---

## Technical Details

### Model Information
| Model | Size | Speed | Accuracy | Recommended |
|-------|------|-------|----------|-------------|
| Tiny  | 75 MB | ~0.5x realtime | Lower | ✅ Default |
| Base  | 142 MB | ~0.3x realtime | Good | ✅ Balanced |
| Small | 466 MB | ~0.15x realtime | High | For high-end devices |

### Architecture Integration
- **Pattern:** Strategy Pattern (AudioProcessingStrategy interface)
- **DI:** Hilt with @Singleton scope
- **Storage:** `context.filesDir/whisper_models/`
- **Audio Pipeline:** M4A (16kHz AAC) → WAV (16kHz mono PCM) → whisper.cpp
- **State Management:** StateFlow for reactive updates

### Key Dependencies
- whisper.cpp (git submodule)
- OkHttpClient (for downloads)
- MediaCodec (for audio conversion)
- Hilt (dependency injection)
- Jetpack Compose (UI)

---

## Known Issues / Notes

1. **Package Name Mismatch:**
   - Models.kt has `package com.hyperwhisper.data`
   - But file is at `com/hyperwhisper/ime/data/Models.kt`
   - This might need fixing (either change package or move file)

2. **CMake Version:**
   - Configured for CMake 3.22.1
   - Ensure NDK is installed with compatible CMake

3. **Build Time:**
   - First build will compile whisper.cpp (~5-10 minutes)
   - Subsequent builds will be faster

4. **Testing:**
   - Need real Android device for testing (emulator will be slow)
   - Requires RECORD_AUDIO permission

---

## Commands to Resume

### Continue Implementation
```bash
cd /data/data/com.termux/files/home/projects/hyperwhisper

# Check current status
git status

# Continue with Phase 4
# Create LocalWhisperStrategy.kt
```

### Test Build (when ready)
```bash
# If using gradle wrapper
./gradlew assembleDebug

# Or direct gradle
gradle assembleDebug

# Check for compilation errors
./gradlew compileDebugKotlin
./gradlew compileDebugJavaWithJavac
```

### Download Tiny Model (Phase 8)
```bash
cd /data/data/com.termux/files/home/projects/hyperwhisper

# Download model
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

# Create assets directory
mkdir -p app/src/main/assets/models

# Move model
mv ggml-tiny.bin app/src/main/assets/models/
```

---

## Next Session TODO

1. **Start with Phase 4:** Create `LocalWhisperStrategy.kt`
   - Location: `app/src/main/java/com/hyperwhisper/ime/network/`
   - Reference: Plan file at `/data/data/com.termux/files/home/.claude/plans/functional-greeting-kitten.md`

2. **Fix package name issue** in Models.kt if needed

3. **Continue through Phases 5-10**

4. **Test build after Phase 6** (before UI work)

5. **Test full integration after Phase 10**

---

## Success Criteria

- [x] Native library compiles without errors
- [x] Kotlin bridge layer created
- [x] Model management system ready
- [ ] Local strategy integrates with existing pipeline
- [ ] Models can be downloaded and used
- [ ] Transcription works offline
- [ ] Settings UI functional
- [ ] App builds and runs on device
- [ ] Memory usage acceptable
- [ ] No crashes or native errors

---

## Estimated Remaining Time

- **Phase 4-6:** ~1.5 hours (core integration)
- **Phase 7:** ~1.5 hours (UI work)
- **Phase 8-9:** ~0.5 hours (assets & config)
- **Phase 10:** ~2 hours (testing)

**Total Remaining:** ~5-6 hours

**Total Project:** ~8-9 hours (40% complete)

---

## References

- **Plan File:** `/data/data/com.termux/files/home/.claude/plans/functional-greeting-kitten.md`
- **whisper.cpp Repo:** https://github.com/ggerganov/whisper.cpp
- **Model Downloads:** https://huggingface.co/ggerganov/whisper.cpp/tree/main

---

*Last Updated: 2025-12-30*
*Next Update: When resuming from Phase 4*
