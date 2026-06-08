package com.hyperwhisper.data.config

import com.google.gson.Gson

/**
 * Renders a [ConfigSnapshot] as JSONC: standard JSON plus `//` comments that
 * document each field's meaning and allowed values. The comments come straight
 * from the [ConfigSchema] registry, so the export document and the LLM
 * prompt's option docs can never drift apart.
 */
object JsoncWriter {

    const val FORMAT_ID = "hyperwhisper-config-v1"

    private val gson = Gson()

    /**
     * @param fields the registry subset to render (e.g. prompt-visible only)
     * @param headerLines optional `//` lines emitted at the top of the document
     */
    fun write(
        snapshot: ConfigSnapshot,
        fields: List<ConfigField>,
        headerLines: List<String> = emptyList(),
        includeFormatId: Boolean = true,
    ): String {
        val root = TreeNode()
        for (field in fields) root.insert(field.path.split('.'), field)

        val sb = StringBuilder()
        sb.append("{\n")
        for (line in headerLines) sb.append("  // ").append(line).append('\n')
        if (includeFormatId) {
            sb.append("  \"format\": ").append(gson.toJson(FORMAT_ID))
            sb.append(if (root.children.isEmpty()) "\n" else ",\n")
        }
        root.render(sb, snapshot, indent = 1)
        sb.append("}\n")
        return sb.toString()
    }

    /** Human-readable allowed-values doc for a field, also used by the prompt. */
    fun allowedValuesDoc(type: ConfigValueType): String? = when (type) {
        is ConfigValueType.Bool -> "true | false"
        is ConfigValueType.IntRange -> "integer ${type.min}..${type.max}"
        is ConfigValueType.Enum -> type.options.joinToString(" | ") { it.canonical }
        is ConfigValueType.EnumSet -> "array of: " + type.options.joinToString(" | ") { it.canonical }
        is ConfigValueType.LanguageCode, is ConfigValueType.Text, is ConfigValueType.OpaqueJson -> null
    }

    private class TreeNode {
        val children = LinkedHashMap<String, TreeNode>()
        var field: ConfigField? = null

        fun insert(segments: List<String>, f: ConfigField) {
            if (segments.size == 1) {
                children.getOrPut(segments[0]) { TreeNode() }.field = f
            } else {
                children.getOrPut(segments[0]) { TreeNode() }.insert(segments.drop(1), f)
            }
        }

        fun render(sb: StringBuilder, snapshot: ConfigSnapshot, indent: Int) {
            val pad = "  ".repeat(indent)
            val entries = children.entries.toList()
            entries.forEachIndexed { index, (key, node) ->
                val comma = if (index == entries.lastIndex) "" else ","
                val leaf = node.field
                if (leaf != null) {
                    val allowed = allowedValuesDoc(leaf.type)
                    sb.append(pad).append("// ").append(leaf.description)
                    if (allowed != null) sb.append(". Allowed: ").append(allowed)
                    sb.append('\n')
                    val json = gson.toJson(ConfigValueResolver.toJson(leaf.type, leaf.get(snapshot)))
                    sb.append(pad).append('"').append(key).append("\": ").append(json).append(comma).append('\n')
                } else {
                    sb.append(pad).append('"').append(key).append("\": {\n")
                    node.render(sb, snapshot, indent + 1)
                    sb.append(pad).append('}').append(comma).append('\n')
                }
            }
        }
    }
}
