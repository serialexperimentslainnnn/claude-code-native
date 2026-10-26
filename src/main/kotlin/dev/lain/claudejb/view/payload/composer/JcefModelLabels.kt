package dev.lain.claudejb.view.payload.composer

import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.protocol.models.ModelInfo
import dev.lain.claudejb.model.session.history.LegacyModels

object JcefModelLabels {

    fun modelDisplayLabel(m: ModelInfo): String {
        val derived = deriveModelLabel(m.value)
        val name = m.displayName.ifBlank { derived }
        val family = name.substringBefore(' ')
        val described = m.description.split(" · ").map { it.trim() }
            .firstOrNull { it.startsWith(family, ignoreCase = true) && hasVersion(it) }
        return described
            ?: m.displayName.takeIf { hasVersion(it) }
            ?: derived.takeIf { hasVersion(it) }
            ?: name
    }

    private val VERSION = Regex("""\b\d+(\.\d+)?\b""")

    private fun hasVersion(label: String): Boolean = VERSION.containsMatchIn(label)

    fun modelLabel(session: ClaudeSession): String {
        val id = session.launch.model ?: session.catalog.preferredDefaultModel()
        session.catalog.models.firstOrNull { it.value == id }?.let { return modelDisplayLabel(it) }
        LegacyModels.labelFor(id)?.let { return it }
        return deriveModelLabel(id)
    }

    fun deriveModelLabel(id: String): String {
        val core = id.removePrefix("claude-").substringBefore('[').trim()
        if (core.isBlank()) return "Claude"
        val parts = core.split('-')
        val versionStart = parts.indexOfFirst { it.toIntOrNull() != null }
        if (versionStart <= 0) {
            return parts.joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } }
        }
        val family = parts.subList(0, versionStart)
            .joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } }
        val version = parts.subList(versionStart, parts.size)
            .takeWhile { it.toIntOrNull() != null }
            .joinToString(".")
        return if (version.isNotEmpty()) "$family $version" else family
    }
}
