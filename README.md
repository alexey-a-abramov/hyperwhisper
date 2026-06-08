# HyperWhisper

A voice + code Android keyboard (InputMethodService) built for driving **Claude Code and other CLI agents in Termux**: dictate, post-process speech with an LLM, then switch to code/terminal layouts — all without leaving the keyboard.

## What it does

### Speech-to-text (ASR)
- **Local**: on-device whisper.cpp (vendored under `app/src/main/cpp/`), ggml `.bin` models — offline, private, no API cost.
- **Cloud**: OpenAI, Groq, Deepgram, AssemblyAI, Google Cloud Speech, AWS Transcribe, Azure AI Speech, Mistral Voxtral, Rev.ai, OpenRouter, Gemini, Hugging Face, self-hosted Whisper, and more (BYOK — see `PROVIDER_KEYS.md`).

### LLM post-processing
- **Local, in-process** — `LocalLlmRouter` picks the engine by model-file extension:
  - `.gguf` → vendored **llama.cpp** (`libllama.so` JNI bridge) with **Vulkan GPU auto-detect**;
  - `.task` / `.litertlm` / `.bin` → **MediaPipe LLM Inference** (Gemma).
- **Cloud / remote**: OpenAI, Anthropic Claude, Google Gemini, DeepSeek, Mistral, Groq, OpenRouter, any OpenAI-compatible endpoint, or a local `llama-server` (e.g. Termux at `127.0.0.1:8080`).

### Voice modes
- Built-in: **Verbatim**, Fix Grammar, Polite, Prompt Formatter, LLM Response — plus user-defined custom modes (name + system prompt).
- **Configuration mode**: speak a settings change ("switch to local whisper", "make the keyboard green") and the LLM emits a **JSONC config patch** that is validated and applied through the config registry (`data/config/` — `ConfigSchema`, `ConfigPatchApplier`, JSONC parser/writer, fuzzy matcher).

### Keyboard modes
- **Dictation (Voice)** — large record button, language/model pipeline chips, walkie-talkie press-and-hold.
- **Text (QWERTY)** — multi-locality layouts with long-press **accent popups**, number row, double-space-to-period.
- **Code** — digits + symbol rows, nav cluster, modifier row.
- **Emoji**, **agent layouts** (Claude Code, OpenCode, Gemini CLI, Codex CLI, Text Snippets — quick-command chips per CLI agent), and an **experimental Terminal layout** that commits literal control bytes and xterm escape sequences (Ctrl+C = 0x03, arrows, Alt-chords) — the only reliable way to reach Termux's PTY.

### Everything else
- **4-language localization**: English, Russian, Arabic (RTL), Spanish — keyboard and settings.
- **History**: transcriptions stored with their original audio (default **on**) for playback and reprocessing.
- **Telemetry** (Room): per-phase session timing feeds adaptive progress estimates; stats screen in Settings.
- **Auto-update** from GitHub Releases with local-APK probing — see `AUTO_UPDATE_INFO.md`.
- 18 color schemes, dynamic color, UI scale, per-app layout memory.

## Tech stack

Kotlin · Jetpack Compose + Material 3 · Hilt · Retrofit/OkHttp · DataStore · Room · native C++ (whisper.cpp + llama.cpp/ggml, CMake/NDK). Code lives under `app/src/main/java/com/hyperwhisper/` (`ime/` for the app proper: `audio`, `data`, `keyboard`, `llm`, `localization`, `network`, `service`, `ui`, `update`, `whisper`).

## Building

**On-device (Termux) is the supported path.** The native libraries (whisper.cpp, llama.cpp) build on-device with the Termux NDK toolchain.

```bash
./build-android.sh        # → builds/app-debug.apk
```

The script auto-applies the Termux `aapt2` override (`pkg install aapt2` if missing). `VERSION_CODE` auto-increments in `gradle.properties`; `versionName` is `1.<versionCode>`.

Note: the PRoot/agent environment used for development sessions has **no JDK** — builds must be run from the Termux side.

**CI** (`.github/workflows/`): `ci-cd.yml` builds on pushes to `master` only; `release.yml` builds and publishes a GitHub Release on `v*` tags (or manual dispatch).

> **Known stale:** `build-cloud.sh` (and parts of `BUILD.md` / `BUILD_SCRIPTS_README.md` / `SIGNING_SETUP.md`) reference a GitHub workflow `build-apks.yml` that no longer exists. There is no on-demand cloud build; the local Termux-side build is the supported path.

## Setup

1. Install the APK, open **HyperWhisper** from the launcher.
2. Enable the keyboard: System Settings → Languages & input → On-screen keyboard → enable **HyperWhisper**; grant microphone permission.
3. Configure in the app:
   - **Transcription**: pick Local Whisper (download a ggml model in Local Models) or a cloud provider + API key.
   - **Post-processing** (optional): pick an LLM provider, or a local model file for Gemma/llama.cpp.
4. Switch to HyperWhisper in any text field and dictate.

## Usage

- Tap the mic to record, tap again to stop; text is inserted at the cursor.
- Pick a voice mode from the mode chip (Verbatim for raw transcription; two-step modes route the transcript through the configured LLM).
- Switch keyboard modes via the mode chips / preset slot; agent layouts give one-tap commands for the CLI on the other end.
- In Termux, the experimental Terminal layout sends real control bytes (^C, ^D, arrows) that IME key events cannot.

## Permissions

`RECORD_AUDIO` (dictation), `INTERNET` / `ACCESS_NETWORK_STATE` (cloud providers, updates), external-storage access (local model files on `/sdcard`, APK update probing), `FOREGROUND_SERVICE` + `POST_NOTIFICATIONS` (model downloads), `WAKE_LOCK`. The IME service itself is guarded by the system `BIND_INPUT_METHOD` permission.

## Repo docs

| File | What |
|---|---|
| `BUILD.md`, `QUICK_BUILD.md`, `BUILD_SCRIPTS_README.md` | Build details (partly stale — see note above) |
| `REDESIGN_ROADMAP.md` | Current multi-phase redesign plan (P0–P4) |
| `RELEASE_NOTES.md` | What the current snapshot adds + recorded decisions |
| `AUTO_UPDATE_INFO.md` | How the in-app updater works |
| `LOCALIZATION.md`, `TRANSLATION_TEMPLATE.md` | i18n |
| `PROVIDER_KEYS.md` | Where to get API keys |
| `SIGNING_SETUP.md`, `RELEASES.md` | Release signing / history |

## License

MIT — see [LICENSE](LICENSE).
