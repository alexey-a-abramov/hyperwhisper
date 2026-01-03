# HyperWhisper - Voice-to-Text Android Keyboard

A production-ready custom Android keyboard (InputMethodService) that replaces standard typing with advanced Voice-to-Text capabilities using LLMs and Whisper.

## Features

- 🎤 **Voice Input**: Record audio and transcribe using AI
- 🧠 **Multiple Processing Modes**: Verbatim, Polite, Casual, Translation, and custom modes
- 🔄 **Strategy Pattern API**: Supports both Whisper-style transcription and Chat Completion with audio
- ⚙️ **BYOK (Bring Your Own Key)**: Configure your own API keys for OpenAI, Groq, or OpenRouter
- ⌨️ **Intuitive Keyboard Layout**: Horizontal space bar, convenient button placement, and delete with repeat
- 📋 **Smart Clipboard**: One-tap paste with live preview
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

### Quick Build

```bash
# Clone the repository
git clone https://github.com/alexey-a-abramov/hyperwhisper.git
cd hyperwhisper

# Build cloud flavor (API-only, ~30 seconds on Termux)
./build-android.sh cloud

# Or build local flavor (with on-device Whisper, ~1 minute on Termux)
./build-android.sh local

# Or build via GitHub Actions
gh workflow run build-apks.yml
```

**Output**: APKs in `builds/local/` or `builds/cloud/`

**Build Flavors**:
- **Cloud**: API-only voice processing (~15-43 MB) - Uses OpenAI/Groq/OpenRouter APIs
- **Local**: On-device Whisper.cpp (~89-110 MB) - Works offline, requires device models

> 📖 **For detailed build instructions, troubleshooting, and architecture overview, see [BUILD.md](BUILD.md)**

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

## Keyboard Layout

The keyboard features an ergonomic layout optimized for voice input:

```
┌────────────────────────────────────────────────┐
│ [←] [Mode Selector ▼]              [⚙️]       │
│ [Provider / Model Info]                   [ℹ️] │
│                                                 │
│     🎤                           [⌫ Delete]     │
│   (Voice)                        [↵ Enter]     │
│                                                 │
│ [📋 Paste]  [━━━━━ SPACE ━━━━━━━━━━━━━━━]     │
└────────────────────────────────────────────────┘
```

### Key Features

- **Voice Button**: Large, centered button on the left for easy thumb access
- **Delete Button**: Hold to repeat (500ms delay, then rapid repeat) - typical keyboard behavior
- **Enter Button**: Positioned above space for quick access
- **Space Bar**: Full-width horizontal button at the bottom
- **Paste Button**: Shows clipboard preview when content is available
- **Mode Selector**: Quick access to voice processing modes
- **Settings**: Direct access to configuration

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

Contributions are welcome! This is a production-ready template you can customize:
- Add more API providers
- Implement caching
- Add voice activity detection
- Support more audio formats
- Add pronunciation feedback

Please feel free to submit issues and pull requests.

## License

MIT License - see [LICENSE](LICENSE) file for details.

This project is free to use for personal and commercial purposes.

## Credits

Built with ❤️ using Kotlin, Jetpack Compose, and modern Android architecture.

## Support

If you encounter issues or have questions:
- Check the [Troubleshooting](#troubleshooting) section
- Open an issue on GitHub
- Review the API provider documentation
