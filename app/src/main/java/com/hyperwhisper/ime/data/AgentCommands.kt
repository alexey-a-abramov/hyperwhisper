package com.hyperwhisper.data

/**
 * Quick-command palettes for coding-agent CLIs. Each entry is something the
 * user types into the agent prompt — slash commands, prefix sigils, or
 * short shortcuts that are otherwise tedious to type on a soft keyboard.
 *
 * Lives as plain data so a new agent only needs an entry in [byMode], no UI
 * wiring. The AgentKeyboard composable renders these as a tap-to-insert grid.
 */
data class AgentCommand(
    /** What gets inserted (typed into the editor). Trailing space optional. */
    val insertion: String,
    /** Short label shown on the key. Defaults to [insertion]. */
    val label: String = insertion,
    /** One-line tooltip / supporting text. Optional. */
    val description: String? = null
)

object AgentCommands {

    private val claudeCode = listOf(
        AgentCommand("/help", description = "List available commands"),
        AgentCommand("/clear", description = "Clear conversation"),
        AgentCommand("/compact", description = "Compact conversation"),
        AgentCommand("/init", description = "Generate CLAUDE.md"),
        AgentCommand("/cost", description = "Show token usage + cost"),
        AgentCommand("/agents", description = "Manage subagents"),
        AgentCommand("/loop", description = "Recurring task loop"),
        AgentCommand("/pr-comments", description = "Show PR review comments"),
        AgentCommand("/release-notes", description = "Show changelog"),
        AgentCommand("/review", description = "Review a PR"),
        AgentCommand("/security-review", description = "Security review"),
        AgentCommand("/model", description = "Switch model"),
        AgentCommand("!", label = "! bash", description = "Run shell command"),
        AgentCommand("#", label = "# memory", description = "Save to memory"),
        AgentCommand("@", label = "@ file", description = "Reference a file"),
        AgentCommand("ultrathink", description = "Maximum thinking budget"),
        AgentCommand("\u001B", label = "Esc", description = "Cancel current action")
    )

    private val openCode = listOf(
        AgentCommand("/help"),
        AgentCommand("/clear"),
        AgentCommand("/exit"),
        AgentCommand("/model", description = "Switch model"),
        AgentCommand("/agent", description = "Switch agent"),
        AgentCommand("/init", description = "Generate AGENTS.md"),
        AgentCommand("/edit", description = "Open editor"),
        AgentCommand("/compact"),
        AgentCommand("/sessions"),
        AgentCommand("/share", description = "Share session"),
        AgentCommand("/themes"),
        AgentCommand("@", label = "@ file"),
        AgentCommand("!", label = "! bash"),
        AgentCommand("\u001B", label = "Esc")
    )

    private val gemini = listOf(
        AgentCommand("/help"),
        AgentCommand("/quit"),
        AgentCommand("/clear"),
        AgentCommand("/chat", description = "Switch chat mode"),
        AgentCommand("/memory", description = "Inspect memory"),
        AgentCommand("/tools", description = "List tools"),
        AgentCommand("/auth", description = "Reauthenticate"),
        AgentCommand("/stats"),
        AgentCommand("@", label = "@ file"),
        AgentCommand("!", label = "! bash"),
        AgentCommand("\u001B", label = "Esc")
    )

    private val codex = listOf(
        AgentCommand("/help"),
        AgentCommand("/clear"),
        AgentCommand("/exit"),
        AgentCommand("/model"),
        AgentCommand("/sandbox", description = "Sandbox mode"),
        AgentCommand("/approval", description = "Approval policy"),
        AgentCommand("/diff", description = "Show diff"),
        AgentCommand("/compact"),
        AgentCommand("@", label = "@ file"),
        AgentCommand("!", label = "! bash"),
        AgentCommand("\u001B", label = "Esc")
    )

    fun byMode(mode: KeyboardInputMode): List<AgentCommand> = when (mode) {
        KeyboardInputMode.AGENT_CLAUDE_CODE -> claudeCode
        KeyboardInputMode.AGENT_OPENCODE -> openCode
        KeyboardInputMode.AGENT_GEMINI -> gemini
        KeyboardInputMode.AGENT_CODEX -> codex
        else -> emptyList()
    }
}
