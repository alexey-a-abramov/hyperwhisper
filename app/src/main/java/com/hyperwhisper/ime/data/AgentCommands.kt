package com.hyperwhisper.data

/**
 * Quick-command palettes for coding-agent CLIs. Each entry is something the
 * user types into the agent prompt — slash commands, prefix sigils, or
 * short shortcuts that are otherwise tedious to type on a soft keyboard.
 *
 * Lives as plain data so a new agent only needs an entry in [byMode], no UI
 * wiring. The AgentKeyboard composable renders these as a tap-to-insert grid.
 */
enum class AgentCategory {
    /** Prompt-prefix sigils and free-form keywords — pinned in a fixed row. */
    INLINE,
    /** Per-session controls: clear, compact, model swap, cost. */
    SESSION,
    /** PR / repo workflow actions: review, security-review, init. */
    CODE,
    /** Rare admin / one-time-per-project: agents, help, release-notes. */
    META,
    /** Hardcoded prompt phrases the user reuses across agents. */
    MACRO
}

/**
 * Hardware-key chord (keycode + meta-state mask) sent to the focused app via
 * the IME's InputConnection. Used for chips that need to fire an actual key
 * combo rather than insert text — e.g. Shift+Tab to cycle Claude Code's
 * normal/auto-accept/plan modes inside the Termux TUI.
 *
 * [keyCode] is `android.view.KeyEvent.KEYCODE_*`; [metaState] is the OR of
 * `KeyEvent.META_*_ON` flags (META_SHIFT_ON, META_CTRL_ON, META_ALT_ON).
 */
data class KeyChord(val keyCode: Int, val metaState: Int = 0)

data class AgentCommand(
    /** What gets inserted (typed into the editor). Empty when [keyChord] is
     *  set — the chip is a key-event sender, not a text inserter. */
    val insertion: String,
    /** Short label shown on the key. Defaults to [insertion]. */
    val label: String = insertion,
    /** One-line tooltip / supporting text. Optional. */
    val description: String? = null,
    /** Visual grouping. SESSION is a sane default for unmarked entries. */
    val category: AgentCategory = AgentCategory.SESSION,
    /**
     * Related commands accessible via long-press on this chip. Lets us surface
     * lower-frequency siblings (e.g. /security-review, /pr-comments) without
     * spending front-page real estate on them.
     */
    val variants: List<AgentCommand> = emptyList(),
    /**
     * When non-null, tapping the chip dispatches this hardware-key chord to
     * the focused app (via [InputConnection.sendKeyEvent]) instead of
     * inserting [insertion]. Used for app-level shortcuts that the target
     * CLI listens for directly — Shift+Tab in Claude Code, Ctrl+L to clear,
     * etc. Label should describe the *effect*, not the chord, since the
     * effect is what the user is reaching for.
     */
    val keyChord: KeyChord? = null
)

object AgentCommands {

    // Ordering: most-tapped commands first. Rare/one-time-per-project commands
    // (/init, /agents, /help) sink to the tail so they don't claim prime real
    // estate. Sigils (@ # !) and ultrathink rank high — they're frequent
    // prompt prefixes, not occasional admin actions. Sigils carry the INLINE
    // category and the keyboard pulls them into a fixed top row above the grid.
    private val claudeCode = listOf(
        // Inline row — sigils and prompt-shaping keywords. `@` lives on QWERTY,
        // so giving it a dedicated full-row slot was overkill; left out here.
        // `#` (memory) was dropped: in practice the auto-memory system
        // captures the same intent without forcing a sigil — keeping the
        // chip just added clutter for a feature people rarely invoked.
        AgentCommand("!", label = "! bash", description = "Run shell command", category = AgentCategory.INLINE),
        // Real key-chord chip — sends Shift+Tab to the focused app. In
        // Claude Code's TUI this cycles the permission mode
        // (normal → auto-accept → plan → normal). Keeping the label
        // outcome-oriented ("Plan / Auto") rather than chord-oriented
        // ("⇧+Tab") because the user wants the result, not the keystroke.
        AgentCommand(
            insertion = "",
            label = "Plan / Auto",
            description = "Cycle Claude Code mode (sends Shift+Tab)",
            category = AgentCategory.INLINE,
            keyChord = KeyChord(
                keyCode = android.view.KeyEvent.KEYCODE_TAB,
                metaState = android.view.KeyEvent.META_SHIFT_ON
            )
        ),
        AgentCommand("ultrathink", description = "Maximum thinking budget", category = AgentCategory.INLINE),
        AgentCommand("\u001B", label = "Esc", description = "Cancel current action", category = AgentCategory.INLINE),
        // Session controls — touched multiple times per work session.
        AgentCommand("/clear", description = "Clear conversation", category = AgentCategory.SESSION),
        AgentCommand("/compact", description = "Compact conversation", category = AgentCategory.SESSION),
        AgentCommand("/resume", description = "Pick a past session to resume", category = AgentCategory.SESSION),
        AgentCommand("/status", description = "Show config / MCP / model status", category = AgentCategory.SESSION),
        AgentCommand("/cost", description = "Show token usage + cost", category = AgentCategory.SESSION),
        AgentCommand("/model", description = "Switch model", category = AgentCategory.SESSION),
        // Workflow / repo actions. /review carries security-review and pr-comments
        // as long-press variants — same tap on the front, less front-page noise.
        AgentCommand(
            "/review",
            description = "Review a PR",
            category = AgentCategory.CODE,
            variants = listOf(
                AgentCommand("/security-review", description = "Security review", category = AgentCategory.CODE),
                AgentCommand("/pr-comments", description = "Show PR review comments", category = AgentCategory.CODE)
            )
        ),
        AgentCommand("/loop", description = "Recurring task loop", category = AgentCategory.CODE),
        AgentCommand("/export", description = "Export the conversation", category = AgentCategory.CODE),
        // Admin / setup. Tapped occasionally — kept around but sunk below the
        // daily-driver session and code rows.
        AgentCommand("/permissions", description = "Manage tool permissions", category = AgentCategory.META),
        AgentCommand("/mcp", description = "Manage MCP servers", category = AgentCategory.META),
        AgentCommand("/config", description = "Open settings", category = AgentCategory.META),
        AgentCommand("/agents", description = "Manage subagents", category = AgentCategory.META),
        AgentCommand("/init", description = "Generate CLAUDE.md", category = AgentCategory.META),
        AgentCommand("/release-notes", description = "Show changelog", category = AgentCategory.META),
        AgentCommand("/doctor", description = "Diagnose installation", category = AgentCategory.META),
        AgentCommand("/help", description = "List available commands", category = AgentCategory.META)
    )

    private val openCode = listOf(
        AgentCommand("@", label = "@ file", category = AgentCategory.INLINE),
        AgentCommand("!", label = "! bash", category = AgentCategory.INLINE),
        AgentCommand("\u001B", label = "Esc", category = AgentCategory.INLINE),
        AgentCommand("/clear", category = AgentCategory.SESSION),
        AgentCommand("/compact", category = AgentCategory.SESSION),
        AgentCommand("/model", description = "Switch model", category = AgentCategory.SESSION),
        AgentCommand("/agent", description = "Switch agent", category = AgentCategory.SESSION),
        AgentCommand("/edit", description = "Open editor", category = AgentCategory.CODE),
        AgentCommand("/sessions", category = AgentCategory.META),
        AgentCommand("/share", description = "Share session", category = AgentCategory.META),
        AgentCommand("/themes", category = AgentCategory.META),
        AgentCommand("/exit", category = AgentCategory.META),
        AgentCommand("/init", description = "Generate AGENTS.md", category = AgentCategory.META),
        AgentCommand("/help", category = AgentCategory.META)
    )

    private val gemini = listOf(
        AgentCommand("@", label = "@ file", category = AgentCategory.INLINE),
        AgentCommand("!", label = "! bash", category = AgentCategory.INLINE),
        AgentCommand("\u001B", label = "Esc", category = AgentCategory.INLINE),
        AgentCommand("/clear", category = AgentCategory.SESSION),
        AgentCommand("/chat", description = "Switch chat mode", category = AgentCategory.SESSION),
        AgentCommand("/memory", description = "Inspect memory", category = AgentCategory.SESSION),
        AgentCommand("/tools", description = "List tools", category = AgentCategory.SESSION),
        AgentCommand("/stats", category = AgentCategory.META),
        AgentCommand("/auth", description = "Reauthenticate", category = AgentCategory.META),
        AgentCommand("/quit", category = AgentCategory.META),
        AgentCommand("/help", category = AgentCategory.META)
    )

    private val codex = listOf(
        AgentCommand("@", label = "@ file", category = AgentCategory.INLINE),
        AgentCommand("!", label = "! bash", category = AgentCategory.INLINE),
        AgentCommand("\u001B", label = "Esc", category = AgentCategory.INLINE),
        AgentCommand("/clear", category = AgentCategory.SESSION),
        AgentCommand("/compact", category = AgentCategory.SESSION),
        AgentCommand("/model", category = AgentCategory.SESSION),
        AgentCommand("/diff", description = "Show diff", category = AgentCategory.CODE),
        AgentCommand("/sandbox", description = "Sandbox mode", category = AgentCategory.SESSION),
        AgentCommand("/approval", description = "Approval policy", category = AgentCategory.SESSION),
        AgentCommand("/exit", category = AgentCategory.META),
        AgentCommand("/help", category = AgentCategory.META)
    )

    // Cross-agent prompt directives. Not nice-to-read truisms — these are
    // operational instructions that, when prepended/appended to a prompt,
    // materially change how Claude Code (or any prompt-following agent)
    // behaves: how it plans, what it skips, when it stops to ask, what it
    // refuses to fabricate. Curated for vibe coding — the workflow where you
    // and the agent ping-pong fast and bad defaults compound across iterations.
    //
    // Lead with a contrarian "stop, plan first" so the user sees the gem on
    // first scroll and the palette feels worth reading.
    //
    // Each entry ends in a space so chaining it onto a prompt requires no
    // cursor work. No trailing newline — most CLIs would auto-submit, and
    // composed prompts deserve a glance before they go.
    private val macros = listOf(
        // The single biggest behavior pivot: agents default to "do" — flip
        // them to "plan, then do" with one sentence.
        AgentCommand(
            "Don't write code yet. Plan first: list the files you'd touch and why, then wait for go-ahead. ",
            category = AgentCategory.MACRO
        ),
        // Anti-stale-context: agents over-trust their internal model of files
        // they've seen before. Force a fresh read.
        AgentCommand(
            "Read the file before editing it — even one you think you know. Your mental model of its current state is stale. ",
            category = AgentCategory.MACRO
        ),
        // Scope-discipline: most damage in vibe coding is from drive-by edits.
        AgentCommand(
            "Make the smallest change that could possibly work. No drive-by refactors, no unrelated cleanup. ",
            category = AgentCategory.MACRO
        ),
        AgentCommand("ultrathink ", category = AgentCategory.MACRO),
        // Anti-defensive-coding: agents reflexively wrap things in try/catch
        // and add null guards that hide real bugs.
        AgentCommand(
            "Don't add try/catch, null guards, or fallbacks I didn't ask for. Let errors surface so I can see them. ",
            category = AgentCategory.MACRO
        ),
        // Anti-comment-noise: most generated comments are filler ("// loop
        // through items"). Suppress them entirely as a default.
        AgentCommand(
            "Don't write comments. If a name doesn't explain itself, rename it instead. ",
            category = AgentCategory.MACRO
        ),
        // Anti-loop: the single most expensive failure mode of vibe coding is
        // an agent thrashing on three variations of the same wrong approach.
        AgentCommand(
            "When stuck for two iterations, stop and ask. Don't try a third variation of the same approach. ",
            category = AgentCategory.MACRO
        ),
        // Precision: forces concrete grounding instead of vague gestures.
        AgentCommand(
            "Ground every claim in a file path and line number. No 'somewhere in the codebase'. ",
            category = AgentCategory.MACRO
        ),
        // Verification gate: agents declare done off vibes; demand evidence.
        AgentCommand(
            "Run the test or build before declaring done. A passing self-test, not 'looks right', is the bar. ",
            category = AgentCategory.MACRO
        ),
        // Anti-fabrication: agents will speculate diagnoses if pushed. Give
        // them permission to say "I don't know yet."
        AgentCommand(
            "If you can't reproduce the bug, say so. Speculation isn't diagnosis. ",
            category = AgentCategory.MACRO
        ),
        // Anti-hallucination on APIs/libraries: training data goes stale,
        // libraries change. Force grounding.
        AgentCommand(
            "Verify library or API behavior against a primary source — docs or source — before relying on it. ",
            category = AgentCategory.MACRO
        ),
        // Throughput: agents serialize tool calls by default and burn the
        // user's time on it.
        AgentCommand(
            "Use parallel tool calls when reads are independent. Sequential reads are a tax I'm paying. ",
            category = AgentCategory.MACRO
        ),
        // Vibe-coding review gate: the user wants to see intent before the
        // change lands.
        AgentCommand(
            "Show me the diff before applying — I want to eyeball intent versus the actual change. ",
            category = AgentCategory.MACRO
        ),
        // Honest pivots: silent change-of-approach is the cousin of silent
        // failure. Make pivots explicit.
        AgentCommand(
            "If you change approach mid-task, say so explicitly. Don't silently pivot. ",
            category = AgentCategory.MACRO
        ),
        // Safety rail: destructive ops need a beat of confirmation, every time.
        AgentCommand(
            "Ask before destructive operations: file deletes, force-push, migrations, dependency removals. ",
            category = AgentCategory.MACRO
        )
    )

    fun byMode(mode: KeyboardInputMode): List<AgentCommand> = when (mode) {
        KeyboardInputMode.AGENT_CLAUDE_CODE -> claudeCode
        KeyboardInputMode.AGENT_OPENCODE -> openCode
        KeyboardInputMode.AGENT_GEMINI -> gemini
        KeyboardInputMode.AGENT_CODEX -> codex
        KeyboardInputMode.AGENT_MACROS -> macros
        else -> emptyList()
    }
}
