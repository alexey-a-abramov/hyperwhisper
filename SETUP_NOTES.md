# Setup Notes for HyperWhisper

## Missing Resources (Required for Build)

### App Icons
You need to add app launcher icons. Create or add icons to these directories:

```
app/src/main/res/
├── mipmap-hdpi/
│   ├── ic_launcher.png (72x72)
│   └── ic_launcher_round.png (72x72)
├── mipmap-mdpi/
│   ├── ic_launcher.png (48x48)
│   └── ic_launcher_round.png (48x48)
├── mipmap-xhdpi/
│   ├── ic_launcher.png (96x96)
│   └── ic_launcher_round.png (96x96)
├── mipmap-xxhdpi/
│   ├── ic_launcher.png (144x144)
│   └── ic_launcher_round.png (144x144)
└── mipmap-xxxhdpi/
    ├── ic_launcher.png (192x192)
    └── ic_launcher_round.png (192x192)
```

#### Quick Solution for Icons

**Option 1: Using Android Studio**
1. Right-click on `res` folder
2. New → Image Asset
3. Choose "Launcher Icons (Adaptive and Legacy)"
4. Configure your icon (use an image or clipart)
5. Click Finish

**Option 2: Create Placeholder Icons Manually**
Run this command to create simple placeholder icons:

```bash
# Create mipmap directories
mkdir -p app/src/main/res/{mipmap-mdpi,mipmap-hdpi,mipmap-xhdpi,mipmap-xxhdpi,mipmap-xxxhdpi}

# Note: You'll need to create actual PNG files
# Use an image editor or online tool like:
# - https://romannurik.github.io/AndroidAssetStudio/
# - https://icon.kitchen/
```

**Option 3: Use a simple colored square (for testing)**
Create a simple 512x512 PNG image with a colored background and text "HW", then use Android Asset Studio to generate all sizes.

## Gradle Wrapper Setup

If Gradle wrapper is missing, initialize it:

```bash
gradle wrapper --gradle-version 8.2
```

Or download and extract Gradle manually:
```bash
cd gradle/wrapper
# Download gradle-wrapper.jar from a working project or Maven Central
```

## Build Commands

### First Build
```bash
./gradlew clean
./gradlew assembleDebug
```

### Install to Device
```bash
./gradlew installDebug
```

### Build Release APK
```bash
./gradlew assembleRelease
```

## Testing the Keyboard

### Enable Developer Options (if not enabled)
1. Go to Settings → About Phone
2. Tap "Build Number" 7 times
3. Go back to Settings → System → Developer Options
4. Enable "USB Debugging"

### Install and Enable Keyboard
```bash
# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Gradle
./gradlew installDebug

# View logs
adb logcat | grep -E "VoiceIME|HyperWhisper"
```

### Enable in System Settings
1. Settings → System → Languages & input
2. On-screen keyboard → Manage keyboards
3. Enable "HyperWhisper Voice Keyboard"
4. Grant microphone permission

## API Configuration

### Get API Keys

**OpenAI**
1. Visit https://platform.openai.com/api-keys
2. Create new API key
3. Use model: `whisper-1`
4. Base URL: `https://api.openai.com/v1`

**Groq**
1. Visit https://console.groq.com/keys
2. Create API key
3. Use model: `whisper-large-v3` or `whisper-large-v3-turbo`
4. Base URL: `https://api.groq.com/openai/v1`

**OpenRouter**
1. Visit https://openrouter.ai/keys
2. Create API key
3. Use model: `google/gemini-flash-1.5` or `openai/gpt-4o-audio-preview`
4. Base URL: `https://openrouter.ai/api/v1`

## Known Issues & Solutions

### Issue: Build fails with "Resource not found"
**Solution**: Add the missing icon resources (see above)

### Issue: Hilt errors
**Solution**: Clean and rebuild:
```bash
./gradlew clean
./gradlew build
```

### Issue: Keyboard doesn't show up
**Solution**:
1. Verify it's enabled in Settings
2. Restart the device
3. Check manifest has `BIND_INPUT_METHOD` permission

### Issue: No audio recorded
**Solution**:
1. Grant microphone permission
2. Test with another recording app
3. Check Android version compatibility (min SDK 26)

### Issue: API calls fail
**Solution**:
1. Verify API key is valid
2. Check base URL format
3. Ensure internet permission is granted
4. Test API with curl/Postman first

## Development Tips

### View Live Logs
```bash
# Filter for our app
adb logcat | grep -E "VoiceIME|HyperWhisper|AudioRecorder"

# Clear logs first
adb logcat -c
adb logcat | grep VoiceIME
```

### Debug Compose UI
Add `LocalInspectionMode` checks for preview:
```kotlin
if (LocalInspectionMode.current) {
    // Preview mode
} else {
    // Real device
}
```

### Test API Without Keyboard
Create a test Activity to test API calls independently.

### Check DataStore Values
```bash
adb shell
run-as com.hyperwhisper
cd files/datastore
cat hyperwhisper_settings.preferences_pb
```

## Production Checklist

- [ ] Replace debug icons with production icons
- [ ] Set up proper signing config
- [ ] Remove or disable logging in release builds
- [ ] Add ProGuard rules for all libraries
- [ ] Test on multiple Android versions (26-34)
- [ ] Test with different screen sizes
- [ ] Implement proper error handling for all API calls
- [ ] Add analytics (optional)
- [ ] Add crash reporting (optional)
- [ ] Create privacy policy
- [ ] Test with different API providers
- [ ] Optimize audio recording settings
- [ ] Add loading states for all operations

## File Structure at a Glance

```
hyperwhisper/
├── app/
│   ├── build.gradle.kts          # App-level Gradle config
│   ├── proguard-rules.pro        # ProGuard configuration
│   └── src/main/
│       ├── AndroidManifest.xml   # App manifest
│       ├── java/com/hyperwhisper/# Source code
│       └── res/                  # Resources (XML, strings, icons)
├── build.gradle.kts              # Project-level Gradle config
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Gradle properties
├── README.md                     # Main documentation
└── SETUP_NOTES.md               # This file
```

## Next Steps

1. **Add Icons**: Create app icons (required)
2. **Build**: Run `./gradlew assembleDebug`
3. **Install**: Connect device and run `./gradlew installDebug`
4. **Configure**: Open app, add API key
5. **Enable**: Enable keyboard in System Settings
6. **Test**: Try in any text field

## Support

For issues or questions:
- Check logs: `adb logcat | grep VoiceIME`
- Review Android InputMethodService documentation
- Test API endpoints independently with curl
- Verify permissions are granted

---

**Ready to build?**
```bash
./gradlew clean assembleDebug
```
