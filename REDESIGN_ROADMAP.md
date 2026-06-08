# HyperWhisper IME Redesign Roadmap

*Generated 2026-06-07 from a multi-agent review (7 subsystem readers → 6 competing redesign proposals → feasibility judging → synthesis + completeness-critic pass; 105 confirmed issues). Keyboard track winner: consistency-first; settings track winner: pipeline-oriented; best ideas from the other four proposals grafted in.*

Target: the **lama-telemetry working tree** (42 modified + ~25 new files), not the S1–S5 screenshots — the tree already fixes several screenshot complaints (single-row dictation header, Backspace-over-Enter stack, locality concept). Commit it first; everything below branches from that snapshot. Every phase ships a working APK to `/sdcard/builds/hyperwhisper/`.

---

## 1. Executive summary — the 5 biggest wins

1. **The Chrome Contract: nothing moves unless the user moved it.** One top strip and one bottom bar, identical slots and anchors in *every* mode including Dictation. Today the gear, Esc, Tab and backspace teleport between `DictationHeader.kt` and `UniversalKeyboardTopStrip.kt` (80% copy-paste siblings), and three bottom-row implementations diverge in contents, Enter color, and SPACE/space casing.
2. **Make the primary use case actually work.** Code mode's Ctrl/Alt/Shift row silently does nothing in Termux (`ExperimentalTerminalKeyboard.kt:38-44` documents why). Promote its byte-emitting chords (^C ^D ^Z ^R ^W ⇧Tab) into Code mode, add a tri-state **TermuxChrome** (AUTO/ON/OFF) that hides the Esc/Tab/nav cluster Termux's extra-keys bar already provides — across Code *and* agent layouts — and gate double-space-to-period out of Code/Agent/terminal editors.
3. **One source of truth per fact.** Collapse `useLocalWhisper` vs `ApiProvider.LOCAL_WHISPER` (and the Gemma twin); extract `ProcessingRouter.needsTwoStep` into a shared predicate so the LLM chip shows exactly when an LLM will run; merge the In-chip/LocalityKey dual ownership of `inputLanguage`; render an amber warning when translation is configured but cannot execute (today the purple Out chip is silently a no-op when LLM=NONE).
4. **Delete ~2,800 dead lines, then build a token layer.** Unreachable NUMPAD/SYSTEM_KEYS/VIBE_CODING branches, `UnifiedModeSwitcher.kt`, duplicate root `MicrophoneButton.kt`, `LayoutSelectorDialog.kt`, six settings orphans. Then `KeyboardTokens.kt` (semantic colors + 4 text tokens) and one `KeySurface` composable replace 7 hand-rolled key surfaces, 171 hardcoded `sp` literals, 545 raw `.dp` literals, and the unexplained orange dot — making the 18 themes, dynamic color, uiScale and fontFamily settings finally reach the keys.
5. **Pipeline settings with verdict-driven logic.** Replace 8 abstract categories with the dataflow the user reasons about (Input → Transcription → Post-process → Output → Appearance → System), each Home row showing effective config + a computed badge. A `Verdict` (Visible/Hidden/Disabled+reason+FixLink) attached to `ConfigField` drives settings UI, `ConfigPatchApplier` validation, *and* keyboard chip states from one registry.

---

## 2. Screen-by-screen findings (code-grounded)

**S1 Dictation idle.** Dead center space: `RecordingSection` gets `weight(1f)` of the fixed 320dp board (`KeyboardScreen.kt:478`) for one 56dp FAB. Engine/mode panels look disabled while enabled: `LanguageModelRow.kt:90-99` uses alpha-0.5 containers + thin outlines — the standard *disabled* affordance — and the genuinely-disabled recording state is near-identical. Two blue "EN" controls overlap: the In chip writes `inputLanguage` only; `LocalityKey` writes layout *and* `inputLanguage` (`applyLocality`, KeyboardScreen.kt:240-261) — each silently clobbers the other. Blue vs purple = `primaryContainer` vs `tertiaryContainer`, no legend. All "orange notification dots" are one `components/LongPressIndicator.kt` (0xFFFFB74D, "has a long-press") on the preset chip, paste pill, and EN key — applied to only some long-pressable keys, while QWERTY's number row uses a *different* convention (superscript hints).

**S2 Recording.** Frame holds, but chips dim via near-identical washed styling; the Out/In chips stay styled active while dead (`LanguageButtons.kt`). Hidden defect: the 3-minute cap has three racing stop paths — `AudioRecorderManager.startTimer()` fires `onMaxDurationReached` every 100ms tick (lines 222-227), and `RecordingViewModel.kt:368-379`'s duration collector can win the race and **discard the audio file**.

**S3 Letters.** Chrome jumps vs S1: gear/Esc/Tab relocate because Dictation renders `DictationHeader` while everything else renders `UniversalKeyboardTopStrip` (Esc/Tab/⌫ trailing, `UniversalKeyboardTopStrip.kt:156-181`). Lowercase "space" = hardcoded literal in `TextKeyboardSection.kt:1206` vs smcp small-caps `strings.space` in `KeyboardBottomBar.kt:183-186`. Number-row long-press hardcodes the US symbol set (TextKeyboardSection.kt:1049-1061) while every `LayoutDefinition.shiftedSymbols` map is dead data; `isRTL` never consumed; `portugueseLayout` maps both 9 and 0 to ")" (`KeyboardLayouts.kt:95`).

**S4 Code.** Esc/Tab visible **three times** (strip + nav row `CodeKeyboard.kt:108-109` + Termux's bar); backspace twice in this keyboard alone, in different colors. The Ctrl/Alt/Shift row dispatches IME meta-state KeyEvents that Termux's PTY ignores (`CodeKeyboard.kt:127-146`) — three keys that silently do nothing in the primary use case, while working byte chords sit quarantined in the opt-in Terminal layout. Bottom row drops comma/period/EN; Enter is translucent grey instead of green. Backtick and pipe are absent from all symbol rows. `handleSpacePress` (KeyboardScreen.kt:216-228) fires double-space-to-period inside shell commands.

**S5 Switcher.** Four live mode-switch mechanisms with three different vocabularies: strip chips; the preset picker Card hardcode-anchored at `padding(top=38.dp, start=92.dp)` (KeyboardScreen.kt:919); a whole-surface 64dp swipe cycle that mis-fires from key drags; and `LayoutSelectorDialog` on space long-press, whose Numpad/Vibe-Coding buttons persist un-normalized enum values that render Code anyway and break the swipe cycle (`keyboardModeOrder.indexOf == -1 → coerced to 0`). Agent/Terminal layouts compete for one preset slot, forcing constant rebinding.

**Settings.** Deep-link `initialProvider` save can merge into the placeholder `ApiSettings()` before DataStore primes and **wipe stored keys** (SettingsScreen.kt:136-151, SettingsViewModel.kt:62). The Cloud/Local pivot *persists* `useLocalWhisper` on tab tap (TranscriptionSection.kt:151-164) — peeking deactivates your engine. About is a fake route (LaunchedEffect-startActivity flicker, SettingsScreen.kt:429-434); ApiCallLogs is an unrouted overlay ignoring system back; diagnostics live on four surfaces with two parallel runners; persist-per-keystroke rebuilds field state mid-typing; `recentEmojis` is surfaced and schema-exposed but never persisted; Home/LocalModels/status strings are hardcoded English in a 4-language app.

---

## 3. Target design: keyboard

### 3.1 The Chrome Contract

**Top strip — one composable, all modes** (merge `DictationHeader.kt` into `UniversalKeyboardTopStrip.kt`):

```
[mic] [Voice] [Text] [mode ⌄] ………… [Esc] [Tab] [⌫] [gear]
```

- Mic tap from any layout jumps to Dictation and starts recording.
- One mode chip opens the switcher, anchored via `onGloballyPositioned` (kill the magic offsets).
- Trailing cluster fixed order/position. Strip ⌫ stays the Code-mode delete home; Dictation/QWERTY additionally keep the working tree's right-stack ⌫ (`BottomActionsRow.kt`) as the thumb-zone delete — **but the stack contributes only the ⌫ half**: its Enter half *is* the bottom bar's `[↵]` cell (same column, same width, the row directly above), never a second Enter. One visual identity via `KeySurface`, two delete homes max, exactly one Enter, never floating.
- **TermuxChrome = AUTO | ON | OFF.** AUTO keys off a shared **`TerminalApps` predicate** — a default package set (`com.termux` plus known forks), user-extensible through Output's per-app overrides, with `EditorInfo.inputType == TYPE_NULL` as a heuristic fallback for unlisted terminal emulators (`KeyboardViewModel.currentPackage` is already tracked at line 48). The same predicate is the **single gate** for byte-vs-KeyEvent chord dispatch and the double-space scope — never three ad-hoc checks. When active, the entire duplicated cluster hides — strip Esc/Tab, Code's arrow group (with Home/End/PgUp/PgDn), **and agent-layout duplicates such as the Claude Code layout's INLINE Esc chip (`AgentCommands.kt:102`)**; ⌫/gear anchors don't shift.

**Bottom bar — one slotted composable** replacing `BottomActionsRow`'s bespoke layout, QWERTY's inline row (TextKeyboardSection.kt:1172-1231), and bare `KeyboardBottomBar`:

```
[paste pill · fixed weight] [EN] [slotA] [SPACE] [slotB] [↵]
```

- Per-mode punctuation slots: `,` / `.` in Voice/Text; **`` ` `` / `|`** in Code (fills the missing-symbols gap without widening the row).
- Enter = `actionSubmit` green everywhere (fix `BottomBarEnter`'s translucent secondaryContainer), and the `[↵]` cell is the **only Enter in every mode**, pixel-anchored: in Dictation/QWERTY the right-stack ⌫ renders in the cell directly above it (see strip bullet); in Code/Agent the cell renders alone. This is what makes P2's "Enter pixel-stable across all modes" acceptance satisfiable — one Enter, one position, ever. SPACE = one render path (`strings.space` + smcp).
- Paste pill: history icon (it's the last *transcription*), tail truncation instead of `take(24)`, fixed weight, renders dimmed-empty instead of disappearing.
- Real system clipboard: expose the wired-but-unused `onInsertClipboard` (VoiceInputMethodService.kt:277) as a dedicated chip in the dictation setup sheet — **not** the paste pill's long-press, which already opens history.

**Switcher — one mechanism.** Mode-chip tap opens an anchored scrim+Card grid of all enabled modes (Code, Emoji, enabled agents) — two taps total, keyboard visible. Entries generate from a **single mode-spec table** keyed by `KeyboardInputMode` that also feeds `ConfigSchema.keyboardModeOptions` (one place per new mode instead of three). The preset-slot rebind dies; the swipe cycle becomes an opt-in setting over the same list (persisted from P2 — see phase plan); mode-change toast only for swipes. Mode state gains a **single writer**: one controller owns persistence, per-app memory, and `requestedLayout` — today three writers compete (the per-switch `LaunchedEffect` persister, per-app memory restore, and the SharedFlow's replay slot). `LayoutSelectorDialog.kt` is deleted in P0.

### 3.2 Per-layout states

- **Dictation idle:** strip / **transcript card** (~110dp reclaimed dead space: last-transcription preview, tap = insert — replacing AgentKeyboard's 18dp recall icon; inline recoverable errors; the "post-processing skipped, inserted raw text" notice that today only logcat sees) / **full-width giant mic band** (whole flexible center tappable) / one plain-text **status line** ("Local Whisper small.en → EN · Verbatim") that taps into a **setup sheet** — moving `LanguageModelRow` off the idle screen entirely, deleting the alpha-0.5 problem rather than restyling it / bottom bar.
- **Setup sheet:** engine chip (tap toggles Local/Cloud, long-press → existing `ProviderModelSelectorDialog`), voice-mode chip, language pipeline control, clipboard-insert action. The LLM chip appears exactly when the shared `needsTwoStep` predicate is true; the "→ XX" half renders amber when translation can't execute (LLM=NONE, or local Whisper + non-en target).
- **Language control (critical constraint):** locality becomes the primary owner with a single setter through `applyLocality`, but the sheet **retains the full ~87-entry SUPPORTED_LANGUAGES picker incl. Auto-detect** as a speech-language override that survives locality cycling — `KeyboardLayout` only covers ~10 localities; deleting the In-dialog outright would orphan ~77 languages.
- **Recording:** mic morphs to red stop **strictly in place** (zero target jump), timer in place, CANCEL pill at fixed position, waveform/elapsed in the transcript card, chips dim via content alpha only.
- **Text:** strip + number row (wire `shiftedSymbols` into long-press; implement `isRTL`; fix the Portuguese typo) + QWERTY + shift row (shift only) + bottom bar.
- **Code (absorbs Terminal):** strip + digits + three symbol rows + a **chord row** replacing Ctrl/Alt/Shift: byte chips from a shared `TerminalBytes.kt` (extracted from `ExperimentalTerminalKeyboard`) — emit PTY bytes when `TerminalApps` matches, KeyEvents otherwise. The nav row is **gated, not deleted**: it collapses to a four-arrow group at the chord row's trailing edge (Home/End/PgUp/PgDn move to arrow long-press), hidden when TermuxChrome is active (the terminal's extra-keys bar provides them) and visible otherwise — non-Termux Code users keep cursor navigation. `EXPERIMENTAL_TERMINAL` retires via `normalize() → CODE` — **the enum member stays** (stored enum-name strings must keep parsing).
- **Double-space-to-period:** gated to Voice/Text and non-terminal editors (via `TerminalApps`) everywhere — and the trigger itself gets fixed: confirm the previous character is actually a space via `getTextBeforeCursor(1)` and no-op while a selection is active. Today `handleSpacePress` (KeyboardScreen.kt:216-228) fires `onDelete()`/`deleteSelected()` unconditionally, destroying live selections.

### 3.3 Token vocabulary — `ui/theme/KeyboardTokens.kt`

- **Colors:** `keySurface`/`keyText` derived from MaterialTheme (themes finally reach the keys; EmojiKeyboard's private palette dies); `actionSubmit` green = Enter **only** (idle mic moves to primary — today record and submit share 0xFF00C853); `actionDelete` red = backspace only; `actionSpace` yellow; `recordActive` red; `statusWait` orange — orange returns to meaning exactly one thing.
- **Typography:** `keyLabel`/`keyLabelSmall`/`chipLabel`/`hint` fed by `createScaledTypography`, replacing 171 `fontSize` literals; uiScale/fontFamily become live on the IME.
- **Metrics:** finish the half-adopted `KeyboardMetrics`: sweep the 545 raw `.dp` literals into metric tokens, fix `PunctKeyWidth` being derived from `BoardHeight` (a width computed from a height, violating the fraction system's own rule), and raise the ~36dp strip chips to the 44dp minimum touch target.
- **Long-press affordance:** kill the orange dot. Adopt the number row's existing convention — a small corner hint glyph showing the long-press result — applied to *every* long-pressable key.
- **One `KeySurface(style: KeyStyle)`** replaces the 7 key-surface families; one radius from `KeyboardMetrics.KeyRadius`; one 500ms long-press constant.

---

## 4. Target design: settings

### 4.1 Pipeline IA (6 stages replace 8 categories)

Home = six `StageStatusRow`s showing *effective config* + computed badge, vocabulary matching the keyboard's (name `ggml-small.en.bin`, not "On-device · Whisper"). Extending the sealed `SettingsRoute` + `AnimatedContent` router is mechanical; add `Stage`, `Logs`, `Health`, `About`, `Stats` cases. Add **settings search** on Home indexing `ConfigSchema` field labels (all four languages), navigating by route + field anchor through the same scroll-to-anchor mechanism FixLinks use — one implementation, two consumers.

1. **Input** — keep-original-audio (from KeyboardBehaviorSection), recording cap (read-only), mic permission. (Walkie-talkie default = *new* persistence work — today in-memory only; small, optional.)
2. **Transcription** — On-device|Cloud pivot as a **pure view switch** ("ACTIVE" tag + explicit "Use this engine" button; the persist-on-tab-tap dies). On-device tab absorbs the Whisper half of `LocalModelsSection`; Cloud tab: provider (LOCAL_WHISPER removed from dropdown), model, key gated by `requiresAuth`, base URL under Advanced. Spoken-input language with locality-coupling note; inline ⚠ for `.en` model + non-English input.
3. **Post-processing** — one LLM engine control (Off/cloud/Gemma/llama.cpp); Gemma rows move here; Voice Modes as sub-list with sealed `kind` (VERBATIM/TRANSFORM/CONFIG) replacing the magic `"verbatim"` id, enforced `isBuiltIn`, and **deletion blocked for the selected mode**. Header states effective behavior from the shared predicate; translation-honesty warning inline.
4. **Output** — *Insertion*: auto-copy, enter-action, double-space **scope**, pending-insert toggle. *Keyboard*: enabled localities, agent keyboards, per-app memory + **Clear** wiring the orphaned `PerAppLayoutMemory.clearAll()`, TermuxChrome tri-state, **per-app overrides** (reduced form: com.termux auto-applies hideNavKeys + double-space off; this list also feeds the `TerminalApps` package set), swipe-cycle and long-press-hints toggles. Note: the TermuxChrome/swipe-cycle persistence fields and an interim toggle ship in P2 — this stage is their permanent UI home, not their birthplace.
5. **Appearance** — fix ColorSchemeSelector scroll nesting; **disable the 18-theme selector when dynamic color is on**, with reason text; affordance legend.
6. **System** — Updates, **Health**, API logs (back-dismissable route, confirmed clear-all), Stats route, Config export/import (snapshot membership per the P0 decision), real About screen, techie toggles. **Health** unifies RetestAllCard + LocalModelsSection's parallel test runner + staleness on one `ConnectionTester` result model; stale = amber, never red.

### 4.2 Conditional-logic rules (Verdict engine)

Attach one verdict function to `ConfigField`: `Verdict = Visible | Hidden | Disabled(reason: (Strings) -> String, fix: FixLink)` (no `StringsKey` type exists — use accessor lambdas or new properties across the 4 language files). Consumed by settings UI (tap a disabled row → explanation sheet), `ConfigPatchApplier` (reject hidden-field patches with reason), and the keyboard (Out chip disabled-with-reason; chip enabled/disabled styling from verdict, not alpha-washing).

| # | Dependency | Effect |
|---|---|---|
| R1 | Engine truth | Existing `LOCAL_WHISPER`/`LOCAL_GEMMA` enum values become sole truth; `useLocal*` derived at read; one-shot migration |
| R2 | Provider → model | On any provider change (picker/patch/import): restore MRU model via a **new helper over `recentlyUsedProviderModels`** (no per-provider getter exists; global 8-entry MRU), else `defaultModels.first()` |
| R3 | `requiresAuth` | Key/URL fields visible iff true; ⚠ iff key blank; per-key chips: Key required / Untested / OK <7d / Retest >7d (neutral tint) from `lastTestedAt` |
| R4 | One routing predicate | `needsTwoStep` extracted (static companion — trivially extractable) → stage-3 header, Home badge, LLM-chip visibility |
| R5 | Translation honesty | `outputLanguage != inputLanguage && !canTranslate` ⇒ amber on stage 3 and the keyboard pipeline chip |
| R6 | File existence | Local engine + missing model file ⇒ red badge (fixes the false-green `llmActive`) |
| R7 | Gemma 401/403 | Dedicated "Needs license" state + HF deep link (`GemmaModelCatalog` already documents it) |
| R8 | View ≠ state | Pivots/tabs/About-row never persist anything; engine changes only via explicit action; implied side effects (model reset, useLocal* sync) previewed via the existing `ConfigDiffList`/confirmation overlay |
| R9 | Badges | Four only: green ✓ verified / amber ⚠ attention / red ✕ broken / neutral ● off — all computed, no decorative dots |

### 4.3 Delete/merge list

Fake About route; `ApiProvider.LOCAL_WHISPER` from the cloud dropdown + dialog cases; "Local models" category (dissolved into stages 2/3; duplicate `buildIntegrationResultsJson` dies); orphans `ProviderStatusCard.kt`, `SectionCard.kt`, `ModeCardWithTooltip`, `ModelInfoDialog.kt` + no-op `onShowLlmInfo`, `LanguageInfoDialogs.kt`, AboutScreen's IntegrationTestSection (lines 779-995), TranscriptionSection's dead download params, dead `AppUpdateSection` import, `showUpdateDialog`; private ToggleRow/GroupHeader duplicates; one of each ASR/LLM selector twin (genericized into `settings/components/kit/`: `ProviderSelector<T>`, `ModelSelector(showFreeFilter)`, `ToggleRow`/`GroupHeader`/`FieldGroup`, generic `ModelRow`, `StageStatusRow`, `Badge(kind)`); the two model downloaders merge into one generic resumable downloader.

---

## 5. Phased roadmap (each phase = shippable APK)

### P0 — Snapshot, correctness, purge (1–2 sessions)

| Task | Files | Effort |
|---|---|---|
| Commit lama-telemetry tree — and **make the `saveOriginalAudioFiles` default flip an explicit decision**: the tree silently flips it to `true` (every recording persisted — a privacy/storage posture change); keep or revert deliberately, and record the verdict + migration note in the rewritten RELEASE_NOTES.md. **Realign JSONC schema paths now** (`input.*`/`transcription.*`/`postProcessing.*`/`output.*`/…) while `data/config/` is untracked and renames are free, and **decide snapshot membership in the same pass**: `ConfigSnapshot` today covers only api+appearance+voiceModes — explicitly rule per-app layout memory, language/provider tracking (`recentlyUsedProviderModels`), and recents in or out of export/import and the Configuration voice mode, recording the decision in the schema | gradle.properties, data/config/*, RecordingModels.kt | M |
| Delete or rewrite the six misleading repo-root docs against the committed tree: README.md, PROJECT_SUMMARY.md, PROJECT_STRUCTURE.txt, AUTO_UPDATE_INFO.md, RELEASE_NOTES.md, WHISPER_INTEGRATION_PROGRESS.md all describe a state that no longer exists and actively mislead anyone — human or agent — reading the repo | repo-root *.md / *.txt | S |
| Recover the live lettered roadmap from `/data/data/com.termux/files/home/.claude/plans/functional-greeting-kitten.md` (Termux home — NOT reachable from the PRoot environment; copy it out via a Termux-side shell, e.g. `cp ~/.claude/plans/functional-greeting-kitten.md /sdcard/`), reconcile it against this document (its roadmap-E bottom-bar work already landed in the working tree; audit the other letters for done/superseded), and commit the merged result into the repo (e.g., `docs/ROADMAP.md`) so the plan stops living only in one machine's home directory | Termux plan file → docs/ROADMAP.md | S |
| Fix deep-link save race: gate `initialProvider` on first *real* DataStore emission (`apiSettingsState` at ApiSettingsRepository line 93 is the natural `loaded` seam) | SettingsScreen.kt:136-151, SettingsViewModel.kt, ApiSettingsRepository.kt | S |
| Single-owner one-shot 3-min timeout: guard `onMaxDurationReached`, delete RecordingViewModel's competing collector, route through the manual stop path | AudioRecorderManager.kt:222-227, RecordingViewModel.kt:368-379, KeyboardViewModel.kt:746-754 | S |
| Delete dead keyboard code (~1,300 lines): TextKeyboardSection NUMPAD/SYSTEM_KEYS/VIBE_CODING + SPECIAL_CHARS, `UnifiedModeSwitcher.kt`, `sections/TopControlsRow.kt`, root `ui/MicrophoneButton.kt`, `components/HamburgerMenu.kt`, EmojiGrid, RecordingTimer, RecordingConfirmationDialog, AWAITING_CONFIRMATION machinery, **`dialogs/LayoutSelectorDialog.kt` entirely** (also fixes the un-normalized persistence + swipe-cycle break). **Keep all enum members**; extend `normalize()` | TextKeyboardSection.kt:169-991, AppearanceModels.kt:132-136 | M |
| Delete settings orphans + dead persisted fields (`VoiceMode.model`, `inputLanguageHint`, `RecordingSettings` — Gson tolerates removed fields) | §4.3 list, RecordingModels.kt | S |
| Swap AgentKeyboard's `DropdownMenu` (real window-token Popup, latent BadTokenException) for scrim+Card; fix `EnterActionSelectorDialog` fully: add scrim-dismiss (currently un-dismissable), collapse or genuinely differentiate the mislabeled **Line Break** action (today an identical `onTextCommit("\n")` to Newline, KeyboardScreen.kt:~819), and derive `isMultiLine` safely instead of defaulting to `true` when `editorInfo` is null | AgentKeyboard.kt:268-288, EnterActionSelectorDialog.kt, KeyboardScreen.kt:819 | M |
| Quick data fixes: `recentEmojis` persistence key (template = `enabledKeyboardLayouts`); **default-value drift sweep** — `autoCopyToClipboard`, `ApiSettings.modelId` (hardcoded 'whisper-1' vs `provider.defaultModels.first()`), `LlmConfig.modelId` ('gpt-4o-mini', absent from OPENAI's `defaultModels`), and `ApiSettingsRepository.snapshot()` serving raw placeholder defaults to interceptors before DataStore primes; Portuguese 0→")" typo; debounce-or-flush field persistence; ApiCallLogs as a route with confirmed clear-all | AppearanceRepository.kt, ApiModels.kt, ApiSettingsRepository.kt, KeyboardLayouts.kt:95, SettingsScreen.kt:450-459 | M |

**Accept:** clean build; no keyboard behavior change except the dead dialog gone and the Enter-action dialog dismissable; mode persists/normalizes correctly; 3-min recording always transcribes; deep-link configure cannot wipe keys; repo-root docs describe the committed tree; the merged roadmap is committed in-repo; the audio-retention default is a recorded decision, not an accident; APK in `/sdcard/builds/hyperwhisper/`.

### P1 — Token substrate (1–2 sessions)

| Task | Files | Effort |
|---|---|---|
| `ui/theme/KeyboardTokens.kt`: semantic colors per §3.3, provided via CompositionLocal, derived from MaterialTheme; move KeyboardScreen.kt:61-68 vals; delete Emoji private palette; idle mic → primary (un-share Enter green) | KeyboardScreen, EmojiKeyboard, MicStates | M |
| **Theme-scheme audit before the keys consume MaterialTheme:** routing key colors through the theme newly exposes the whole keyboard to the 18 synthesized ColorSchemes — contrast-check every container/on-container pair (the alpha-copied containers are flagged fragile) and fix TERMINAL_DARK's unreadable onPrimary=Black on 0xFF2C2C2C; otherwise P1 trades hardcoded-but-legible for theme-driven-but-broken (P4's dynamic-color gating complements, not replaces, this) | Theme.kt / color-scheme definitions | M |
| Key typography tokens replacing 171 sp literals; plumb uiScale/fontFamily | Type.kt, all keyboard ui | M |
| Finish `KeyboardMetrics` adoption alongside the token sweep: 545 raw `.dp` literals → metric tokens; fix `PunctKeyWidth` derived from `BoardHeight` (width-from-height rule violation); raise the ~36dp strip chips to the 44dp minimum touch target | KeyboardMetrics.kt, strip/chip composables, all keyboard ui | M |
| One `KeySurface(style)` replacing the 7 surface families; one radius, one 500ms long-press constant | KeyboardButtons, CodeKeyboard, AgentKeyboard, ExperimentalTerminalKeyboard, strip/header chips | L |
| Replace `LongPressIndicator` dot with corner hint glyph, applied to every long-pressable key | components/LongPressIndicator.kt + 5 call sites | S |

**Accept:** every key renders through KeySurface/tokens; switching theme/dynamic color/uiScale visibly changes the keys; all 18 themes pass a key-legibility spot-check (TERMINAL_DARK included); no strip touch target below 44dp; orange appears only as `statusWait`; screenshots of all 5 modes show consistent radius/casing.

### P2 — The Chrome Contract (2–3 sessions)

| Task | Files | Effort |
|---|---|---|
| Merge DictationHeader → UniversalKeyboardTopStrip; fixed trailing cluster; geometry-anchored switcher grid from the **mode-spec table** (also feeds `ConfigSchema.keyboardModeOptions`); retire preset slot; swipe cycle → opt-in setting; toast only on swipe; **single mode-state writer**: one controller owns persistence, per-app memory, and `requestedLayout`, retiring the per-switch `LaunchedEffect` persister and the SharedFlow replay-slot race | DictationHeader.kt, UniversalKeyboardTopStrip.kt, KeyboardScreen.kt:341-418/897-984, KeyboardViewModel.kt | L |
| Unified slotted bottom bar (per-mode slotA/B; green Enter; one SPACE path; tail-truncated fixed-weight paste pill); the `[↵]` cell is the **single Enter in every mode** — Dictation/QWERTY's right-stack ⌫ renders in the cell directly above it via KeySurface, never a second Enter (§3.1) | KeyboardBottomBar.kt, BottomActionsRow.kt, TextKeyboardSection.kt:1172-1231 | L |
| Code absorbs Terminal: extract `TerminalBytes.kt`; chord row replaces Ctrl/Alt/Shift (PTY bytes when `TerminalApps` matches, KeyEvents otherwise); nav row collapses into the **TermuxChrome-gated four-arrow group** (Home/End/PgUp/PgDn on arrow long-press) so non-Termux Code users keep navigation; add `` ` `` and `|`; `normalize(EXPERIMENTAL_TERMINAL) → CODE` | CodeKeyboard.kt:108-146, ExperimentalTerminalKeyboard.kt | M |
| TermuxChrome tri-state over the shared **`TerminalApps` predicate** (default package set `com.termux` + known forks, user-extensible via Output's per-app overrides, `inputType == TYPE_NULL` heuristic fallback — the single gate for AUTO, chord dispatch, and double-space scope); the gated cluster covers strip Esc/Tab, the Code arrow group, **and the Claude Code agent layout's INLINE Esc chip** (its fourth Esc in Termux today); **persist `termuxChrome` + `swipeModeCycle` in AppearanceSettings/AppearanceRepository in this phase**, with an interim toggle row in the existing KeyboardBehaviorSection — P4's Output stage only relocates the UI, otherwise swipe is unreachable for two phases | KeyboardViewModel.kt:48, AgentCommands.kt:102, AppearanceModels.kt, AppearanceRepository.kt, KeyboardBehaviorSection.kt | M |
| Double-space-to-period: gate to Voice/Text + non-terminal (`TerminalApps`) **and fix the residual trigger bug** — verify the previous character is a space via `getTextBeforeCursor(1)` and no-op while a selection is active; today `handleSpacePress` blindly fires `onDelete()`/`deleteSelected()`, destroying live selections | KeyboardScreen.kt:216-228 | S |
| Wire `shiftedSymbols` long-press, implement `isRTL` | TextKeyboardSection.kt:1049-1061, KeyboardLayouts.kt | M |

**Accept:** screen-record mode cycling — gear/Esc/Tab/⌫/space/Enter pixel-stable across all modes, with **exactly one Enter ever rendered**; in Termux: no duplicate Esc/Tab row, the Claude Code agent layout shows a single Esc, ^C actually interrupts a process, Code shows backtick/pipe; outside Termux, Code still shows the arrow group with Home/End/PgUp/PgDn on long-press; double space in Code inserts two spaces, and in Voice/Text never fires after a non-space character or over an active selection; with Russian locality, number-row long-press yields the Russian `shiftedSymbols` set; the Arabic layout renders right-to-left; TermuxChrome and swipe-cycle settings persist and are toggleable from settings in this build.

### P3 — Dictation redesign + flow ride-alongs (2–3 sessions)

| Task | Files | Effort |
|---|---|---|
| Transcript card + giant mic band + status line + setup sheet per §3.2; in-place mic→stop morph; LanguageModelRow leaves the idle screen | RecordingSection.kt, LanguageModelRow.kt, KeyboardScreen.kt:445-503 | L |
| **One progress estimator:** the rebuilt processing surface consumes only the telemetry-driven adaptive estimates (commit 1964efe); delete `ProcessingIndicator`'s competing wall-clock extrapolator and the circular synthetic ETA it re-ingests as if measured | ProcessingIndicator.kt, telemetry estimator | S |
| Extract `needsTwoStep` to shared predicate; LLM-chip visibility + amber translate warning from it | ProcessingRouter.kt:46-65, LanguageModelRow.kt:68 | S |
| Merged language control: locality primary via single `applyLocality` setter; full 87-language + Auto-detect override retained in the sheet | KeyboardScreen.kt:240-261, LocalityKey.kt, LanguageButtons.kt | M |
| Flow fixes: m4a duration/format — **consolidate all three wrong duration implementations** (TranscriptionViewModel's, TranscriptionStrategy.kt:101-106's WAV-header fallback math applied to m4a files, and `VoiceRepository.calculateAudioDuration`) into one MediaMetadataRetriever-based helper; save `.m4a`; explicit format enum, not extension; pending-insert buffer for keyboard-hidden completions; visible "post-processing skipped" in the card; skip redundant local-Gemma rewrite on the two-step transcription leg | TranscriptionViewModel.kt:304-340, TranscriptionStrategy.kt:101-106, VoiceRepository.kt:148-159, ChatCompletionStrategy.kt:58-63, InputConnectionController.kt:31-40, LocalProcessingStrategy.kt:100-102 | L |

**Accept:** idle dictation has no disabled-looking-but-tappable surfaces; recording moves zero targets; LLM=NONE + Out≠In shows amber, not silence; dictating with screen locked commits on next keyboard open; LLM failure shows the skipped notice; reprocessing a history file sends the `m4a` format tag and a realistic duration; processing shows exactly one ETA, sourced from telemetry.

### P4 — Settings pipeline + verdict engine (3–4 sessions)

| Task | Files | Effort |
|---|---|---|
| Engine-duality collapse (R1) with one-shot migration + ConfigDiffList preview of implied changes; **retire the deprecated three-layer fallback in the same pass**: freeze the live writers of `ApiSettings.baseUrl` (`saveApiSettings`/`resetToDefaults`/`setProviderAndModel` all still write it), migrate at read, then drop it along with `LlmConfig`'s deprecated `apiKey`/`customBaseUrl`/`requiresAuth` mirror fields (read-time migration; never written back) | ApiSettingsRepository, LlmConfig, ConfigSchema, ProcessingRouter, ConnectionTester | L |
| **Decide-and-fix HuggingFace routing:** it is declared text-only in `needsTwoStep` yet `TranscriptionStrategy` sends it multipart audio and `ChatCompletionStrategy` sends base64 audio chat — pick one contract (audio-capable provider or text-only post-processor) and align the predicate and both strategies; rules R1–R9 inherit the contradiction until this lands | ProcessingRouter.kt, TranscriptionStrategy.kt, ChatCompletionStrategy.kt | S |
| Verdict on `ConfigField` + rules R2–R9; keyboard consumes verdicts for chip states | data/config/*, ConfigPatchApplier | L |
| Shared design kit, then the 6 stage screens; dissolve Local models; Health surface; real routes (About/Stats/Logs); per-app overrides + clearAll in Output (relocating P2's interim TermuxChrome/swipe toggles to their permanent home) | SettingsScreen.kt router, sections/* | XL |
| MRU model-restore helper over `recentlyUsedProviderModels`; Gemma NeedsLicense state; dynamic-color theme gating; VoiceMode `kind` + selected-mode delete block (read-time migration in `normalizeModes`); **the downloader merge resolves the write-only SHA-256**: every model selection currently hashes files up to 3GB and the hash is never read back — either verify on download-complete/load or delete the computation | ProviderModelTrackingRepository, GemmaModelDownloader, VoiceModesRepository | M |
| i18n sweep (Home/LocalModels/status/update/OpenRouter strings → 4 Strings files) **including the keyboard-side regressions**: `LocalizedDisplayNames`' `else -> nativeName` fallback (defeats the exhaustive-`when` protection — restore per-language exhaustiveness), RecordingSection's hardcoded 'Esc'/'Tab', the walkie-talkie toasts (KeyboardViewModel.kt:703-721), and the KeyboardScreen.kt:705/741 overlay titles | localization/*, LocalizedDisplayNames.kt, RecordingSection.kt, KeyboardViewModel.kt:703-721, KeyboardScreen.kt:705/741 | M |
| Settings search: index built from `ConfigSchema` field labels (per active language); results navigate by route + field anchor via the **same scroll-to-anchor mechanism FixLinks use** — one implementation, two consumers | ConfigSchema, SettingsScreen.kt router, FixLink/anchor plumbing | M |

**Accept:** Home shows six stage rows with truthful badges; tab-peeking changes nothing; a voice patch changing provider can't strand a stale modelId; missing Gemma file = red, stale test = amber; export/import round-trips the new paths (including whatever the P0 snapshot-membership decision admitted); searching "double" (or its ru/es/ar label) surfaces the double-space scope row and tapping it lands scrolled-to-and-highlighted in Output; HuggingFace either transcribes audio or never receives it — predicate and strategies agree; no code path writes `ApiSettings.baseUrl` or the `LlmConfig` mirror fields; selecting a local model no longer triggers a multi-GB hash; no English leakage under ru/ar/es — including keyboard toasts and overlay titles; Arabic RTL spot-check.

---

## 6. Risks & migration notes

- **Never delete `KeyboardInputMode`/provider enum members.** Enum-name strings persist in DataStore (`presetKeyboardMode`, `lastKeyboardInputMode`, per-app memory) and config imports; removal breaks `valueOf` on existing installs. Retire via `normalize()` only; keep legacy values parse-only.
- **Engine collapse needs a one-shot migration** mapping `useLocalWhisper=true` → provider=LOCAL_WHISPER (and Gemma twin), idempotent like the existing key-migration pattern in `ApiSettingsRepository`. Test the matrix: cloud-key-only users, local-only users, contradictory state (LOCAL_WHISPER selected + flag false).
- **Deprecated mirrors need a write-side freeze before deletion:** `ApiSettings.baseUrl` and `LlmConfig.apiKey/customBaseUrl/requiresAuth` are still actively written today; the P4 retirement must first stop every writer, then migrate at read, and only then drop the fields — same Gson-tolerance pattern as VoiceMode below.
- **Schema freeze ordering:** the JSONC path renames and the snapshot-membership decision (P0) must land before any release exposing config export, or imports break later. The registry is untracked today — this is the only free moment.
- **Working-tree-first:** all redesign branches from the committed lama-telemetry snapshot; drafting against the S1–S5 screenshots re-solves solved problems (header rework, backspace stack, locality) or conflicts with in-flight files (DictationHeader, RecordingSection, BottomActionsRow). The committed snapshot also bakes in the `saveOriginalAudioFiles=true` flip — P0 makes that an explicit, release-noted decision rather than a side effect.
- **IME window constraints are real:** no Dialogs/BackHandler (BadTokenException; no OnBackPressedDispatcherOwner). All overlays standardize on scrim+Card with scrim-tap dismiss; verify the AgentKeyboard DropdownMenu replacement in P0 before P2 standardizes.
- **Vertical budget:** the chord row + the TermuxChrome-gated arrow group must net out within the fixed 320dp `BoardHeight` in *both* gate states; making BoardHeight responsive (landscape/size setting) is deliberately deferred — `KeyboardMetrics`' fraction system supports it later without rework (after P1 fixes its `PunctKeyWidth`-from-`BoardHeight` violation).
- **`TerminalApps` is a predicate, not a string:** every terminal-detection consumer (TermuxChrome AUTO, byte-chord dispatch, double-space scope) goes through the one shared predicate; adding a fork or a new terminal app is a package-list entry or per-app override, never a new hardcoded comparison.
- **VoiceMode JSON:** dropping `model`/`inputLanguageHint` and adding `kind` rides Gson's tolerance for unknown/missing fields; do the read-time migration in `VoiceModesRepository.normalizeModes` (existing pattern) and never write the dead fields back.
- **Per-keystroke → debounced persistence** changes save timing; flush on blur/dispose and on Activity stop so force-closes don't lose the last edit.
- **Package/directory mismatch** (`com.hyperwhisper.ui` vs `ime/ui/`): fix opportunistically per-file during P1/P2 churn when moves are cheap; never as a standalone big-bang rename mid-phase.
- **Each phase gates on:** clean `gradlew assembleRelease` on-device, manual smoke of dictate→insert in Termux + one cloud provider, APK copied to `/sdcard/builds/hyperwhisper/`, and a tagged commit — restoring the checkpoint discipline the 349→367 uncommitted version drift lost.