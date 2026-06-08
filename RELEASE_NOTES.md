# Release Notes

## Snapshot: lama-telemetry (VERSION_CODE 368+)

This snapshot folds in the work from the `lama-telemetry` branch plus the **P0** pass of the redesign (`REDESIGN_ROADMAP.md`).

### New capabilities
- **Voice-driven configuration** — a Configuration voice mode turns spoken settings changes into a validated **JSONC config patch**, applied through a typed config registry (`data/config/`: `ConfigSchema`, `ConfigPatch`, `ConfigPatchApplier`, JSONC parser/writer, fuzzy matcher). Config paths are organised under pipeline namespaces (`input.*`, `transcription.*`, `postProcessing.*`, `output.*`, `appearance.*`, `system.*`).
- **Locality system** — multi-locality QWERTY with a locality selector sheet and long-press **accent popups** (`LocalitySelection`, `AccentKeyWithPopup`, `AccentMap`, `LocalityKey`).
- **Config export / import** — snapshot of API + appearance + voice-mode settings (device-local state — per-app layout memory, provider-model MRU, and recent emojis are deliberately excluded).
- **llama.cpp GGUF post-processing** with **Vulkan GPU auto-detect** alongside the existing MediaPipe/Gemma path; engine chosen by model-file extension.
- **Telemetry-driven progress** — per-phase Room session timing feeds adaptive progress estimates; a stats screen in Settings.

### Behaviour change — recorded decision
- **`saveOriginalAudioFiles` now defaults to `true`.** Original recording audio is kept after a successful transcription so History can play it back and reprocess it. This was an unset-default flip (previously `false`); existing installs keep whatever preference they already stored — only the new-install default changed. Disable it in Settings if you prefer audio to be discarded after transcription. (The model-side data-class default was also corrected to match the repository read-fallback so the value is consistent at every read.)

### P0 correctness fixes
- **Deep-link configure can no longer wipe stored API keys** — the `initialProvider` save now waits for the first real DataStore emission and merges against persisted state instead of placeholder defaults (it also stops resetting `localModelSettings`/`lastTestedAt` on every cloud save).
- **3-minute recordings always transcribe** — the recording cap now stops exactly once through the same path as a manual stop; the competing duration collector that could discard the audio file is gone.
- **Settings field edits are debounced** (~400 ms, flush-on-dispose) instead of persisting on every keystroke.
- **API Call Logs is a real navigable screen** with working system-back and a confirmed clear-all.
- **Default-value drift removed** — transcription `modelId` derives from the provider's own default list instead of a hardcoded `whisper-1`; `autoCopyToClipboard` defaults agree across model and repository.

### Cleanup
- ~1.7k net lines removed: dead keyboard layouts (NUMPAD/SYSTEM_KEYS/VIBE_CODING render branches + SPECIAL_CHARS data), `UnifiedModeSwitcher`, `LayoutSelectorDialog`, duplicate root `MicrophoneButton`, `HamburgerMenu`, `RecordingTimer`, the awaiting-confirmation UI machinery, and several orphaned settings cards/dialogs. All persisted enum members were kept (legacy values still parse and are coerced via `normalize()`).
- `AgentKeyboard`'s mode dropdown was replaced with a scrim+Card overlay (avoids a latent IME-window `BadTokenException`); the Enter-action dialog is now dismissable and its duplicate "Line Break"/"Newline" actions collapsed.
- `recentEmojis` are now actually persisted.

### Build
On-device (Termux) build is the supported path: `./build-android.sh` → `builds/app-debug.apk`. `VERSION_CODE` auto-increments per build. The development PRoot/agent environment has no JDK; compile from Termux. See `README.md`.
