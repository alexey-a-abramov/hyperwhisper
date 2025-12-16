# HyperWhisper - Voice-to-Text Android Keyboard

A production-ready custom Android keyboard (InputMethodService) that replaces standard typing with advanced Voice-to-Text capabilities using LLMs and Whisper.

## Features

- 🎤 **Voice Input**: Record audio and transcribe using AI
- 🧠 **Multiple Processing Modes**: Verbatim, Polite, Casual, Translation, and custom modes
- 🔄 **Strategy Pattern API**: Supports both Whisper-style transcription and Chat Completion with audio
- ⚙️ **BYOK (Bring Your Own Key)**: Configure your own API keys for OpenAI, Groq, or OpenRouter
- 🎨 **Modern UI**: Built with Jetpack Compose and Material 3
- 🏗️ **Clean Architecture**: MVVM pattern with Hilt dependency injection

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt (Dagger)
- **Networking**: Retrofit + OkHttp
- **Storage**: Jetpack DataStore (Preferences)
- **Concurrency**: Kotlin Coroutines & Flow
- **Audio**: MediaRecorder (M4A/AAC format)

## Project Structure

```
app/src/main/java/com/hyperwhisper/
├── HyperWhisperApplication.kt          # Hilt Application class
├── audio/
│   └── AudioRecorderManager.kt         # Audio recording & Base64 encoding
├── data/
│   ├── Models.kt                       # Domain models & API DTOs
│   └── SettingsRepository.kt           # DataStore preferences management
├── di/
│   ├── AppModule.kt                    # App-level DI
│   └── NetworkModule.kt                # Network & Retrofit DI
├── network/
│   ├── ApiService.kt                   # Retrofit API interfaces
│   ├── AudioProcessingStrategy.kt      # Strategy pattern implementations
│   └── VoiceRepository.kt              # Main repository for voice processing
├── service/
│   └── VoiceInputMethodService.kt      # InputMethodService implementation
└── ui/
    ├── KeyboardScreen.kt               # Main keyboard Compose UI
    ├── KeyboardViewModel.kt            # Keyboard state management
    ├── settings/
    │   ├── SettingsActivity.kt         # Settings Activity
    │   ├── SettingsScreen.kt           # Settings Compose UI
    │   └── SettingsViewModel.kt        # Settings state management
    └── theme/
        ├── Theme.kt                    # Material 3 theme
        └── Type.kt                     # Typography definitions
```

## API Strategy Pattern

The app implements two distinct API strategies:

### Strategy A: Transcription (Whisper-style)
- **Endpoint**: `POST /v1/audio/transcriptions`
- **Use case**: Pure verbatim speech-to-text
- **Format**: Multipart/Form-data with audio file
- **Providers**: OpenAI, Groq

### Strategy B: Chat Completion with Audio
- **Endpoint**: `POST /v1/chat/completions`
- **Use case**: Transformations (Translation, Polishing, Summarizing)
- **Format**: JSON with base64 audio in messages
- **Providers**: OpenRouter, OpenAI (GPT-4o Audio)

## Setup & Installation

### Prerequisites

1. Android Studio Hedgehog or later
2. Android SDK 26+ (minimum), 34 (target)
3. Kotlin 1.9.20+
4. Gradle 8.2+

### Build Steps

1. **Clone the repository**
   ```bash
   cd /data/data/com.termux/files/home/projects/hyperwhisper
   ```

2. **Add app icons** (Required)
   Place your app icons in:
   - `app/src/main/res/mipmap-hdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-mdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`

   Or use Android Studio's Image Asset tool to generate them.

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on device**
   ```bash
   ./gradlew installDebug
   ```

### Enabling the Keyboard

1. Open **Settings** → **System** → **Languages & input** → **On-screen keyboard**
2. Tap **Manage on-screen keyboards**
3. Enable **HyperWhisper Voice Keyboard**
4. Grant microphone permission when prompted
5. Select HyperWhisper as your keyboard in any text field

### Configuration

1. Open the HyperWhisper app from your launcher
2. Configure API settings:
   - **API Provider**: Choose OpenAI/Groq or OpenRouter
   - **Base URL**: e.g., `https://api.openai.com/v1`
   - **API Key**: Your API key (sk-...)
   - **Model ID**: e.g., `whisper-1` or `google/gemini-flash-1.5`
3. Add custom voice modes (optional):
   - Tap the **+** button in Voice Modes section
   - Enter a name and system prompt

## Usage

1. Tap in any text field to bring up the keyboard
2. Select a voice mode from the dropdown (Verbatim, Polite, etc.)
3. Tap the large microphone button to start recording
4. Speak your message
5. Tap again to stop and process
6. The transcribed/transformed text will be automatically inserted

## Voice Modes

### Built-in Modes

- **Verbatim**: Pure transcription without modifications
- **Polite**: Rewrites speech to be polite and professional
- **Casual**: Converts to casual, friendly tone
- **Translate to English**: Transcribes and translates to English

### Custom Modes

Create your own modes with custom system prompts:
- "Make it funny"
- "Technical documentation style"
- "Summarize in bullet points"
- "Translate to Spanish"

## API Configuration Examples

### OpenAI Whisper
```
Provider: OpenAI / Groq
Base URL: https://api.openai.com/v1
Model ID: whisper-1
```

### Groq Whisper
```
Provider: OpenAI / Groq
Base URL: https://api.groq.com/openai/v1
Model ID: whisper-large-v3
```

### OpenRouter (GPT-4o Audio)
```
Provider: OpenRouter
Base URL: https://openrouter.ai/api/v1
Model ID: openai/gpt-4o-audio-preview
```

### OpenRouter (Gemini Flash)
```
Provider: OpenRouter
Base URL: https://openrouter.ai/api/v1
Model ID: google/gemini-flash-1.5
```

## Permissions

The app requires the following permissions:

- **RECORD_AUDIO**: To record voice input
- **INTERNET**: To communicate with API services
- **BIND_INPUT_METHOD**: System permission for keyboard services

## Architecture Details

### MVVM with Clean Architecture

- **Data Layer**: Repositories, API services, DataStore
- **Domain Layer**: Models, use cases (implicit in ViewModels)
- **Presentation Layer**: ViewModels, Compose UI

### Dependency Injection (Hilt)

All major components are injected:
- `AudioRecorderManager`
- `SettingsRepository`
- `VoiceRepository`
- `TranscriptionStrategy`
- `ChatCompletionStrategy`

### State Management

Uses Kotlin Flow and StateFlow for reactive state:
- Recording state (IDLE, RECORDING, PROCESSING, ERROR)
- API settings
- Voice modes
- Transcribed text

## Testing Notes

### Unit Testing
- Test ViewModels with mocked repositories
- Test strategy selection logic
- Test API request/response models

### Integration Testing
- Test IME lifecycle
- Test audio recording flow
- Test API integration with test keys

### Manual Testing Checklist
- [ ] Keyboard appears in any text field
- [ ] Microphone permission is requested and granted
- [ ] Recording starts and stops correctly
- [ ] Audio is processed and text is committed
- [ ] Settings are persisted across sessions
- [ ] Custom modes can be added and deleted
- [ ] Different API providers work correctly

## Troubleshooting

### Keyboard doesn't appear
- Ensure it's enabled in System Settings
- Restart the device
- Check logcat for errors: `adb logcat | grep VoiceIME`

### API errors
- Verify API key is correct
- Check base URL format (must end with `/v1` or `/api/v1`)
- Ensure model ID matches the provider
- Check network connectivity

### No audio recorded
- Grant microphone permission
- Check if other apps can record audio
- Try restarting the recording

### Text not committed
- Check if the input field accepts text input
- Look for InputConnection errors in logcat

## Development

### Adding a new voice mode
1. Open Settings in the app
2. Tap **+** in Voice Modes section
3. Enter name and system prompt
4. Mode is saved to DataStore automatically

### Modifying API behavior
Edit `AudioProcessingStrategy.kt` to change how audio is processed for each strategy.

### Changing UI
Edit Compose files in `ui/` package. The keyboard UI is in `KeyboardScreen.kt`.

## Contributing

This is a production-ready template. Customize it for your needs:
- Add more API providers
- Implement caching
- Add voice activity detection
- Support more audio formats
- Add pronunciation feedback

## License

This project is provided as-is for educational and commercial use.

## Credits

Built with ❤️ using Kotlin, Jetpack Compose, and modern Android architecture.
