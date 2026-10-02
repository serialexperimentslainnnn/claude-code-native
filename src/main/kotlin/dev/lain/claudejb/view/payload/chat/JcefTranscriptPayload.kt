package dev.lain.claudejb.view.payload.chat

import dev.lain.claudejb.model.diff.DiffPresenter
import dev.lain.claudejb.model.permission.vocab.SecurityRule
import dev.lain.claudejb.model.session.transcript.EntryDTO
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.session.transcript.TranscriptEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object JcefTranscriptPayload {

    fun entryJson(e: TranscriptEntry, order: Int): JsonObject = buildJsonObject {
        put("id", e.id)
        put("order", order)
        put("speaker", e.speaker.name)
        put("text", e.text)
        e.meta?.let { put("meta", it) }
        e.toolTitle?.let { put("title", it) }
        e.toolUseId?.let { put("toolUseId", it) }
        e.parentToolUseId?.let { put("parent", it) }
        e.filePath?.let { put("filePath", it) }
        e.commandText?.let { put("command", it) }
        e.messageText?.let { put("message", it) }
        guardFields(e.blockedRule, e.bypassedRule, e.bypassAction)
        put("state", e.toolState.name)
        put("elapsed", e.elapsedSeconds)
        reviewableFlag(e.speaker == Speaker.TOOL && e.toolUseId != null && e.reviewable)
        if (e.places.isNotEmpty()) {
            put(
                "places",
                buildJsonArray {
                    e.places.forEach { place ->
                        add(
                            buildJsonObject {
                                put("label", place.label)
                                put("href", place.href)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun JsonObjectBuilder.reviewableFlag(reviewable: Boolean) {
        if (reviewable) put("reviewable", true)
    }

    private fun JsonObjectBuilder.guardFields(blockedRule: String?, bypassedRule: String?, bypassAction: String?) {
        blockedRule?.let { rule ->
            put("blockedRule", rule)
            put("blockedRuleWarns", SecurityRule.from(rule)?.whitelistable == false)
        }
        bypassedRule?.let { put("bypassedRule", it) }
        bypassAction?.let { put("bypassAction", it) }
    }

    fun batchJson(items: List<Pair<TranscriptEntry, Int>>): String =
        JsonArray(items.map { (e, order) -> entryJson(e, order) }).toString()

    fun appendJson(id: Long, delta: String): String = buildJsonObject {
        put("id", id)
        put("delta", delta)
    }.toString()

    fun trimJson(removedIds: List<Long>, total: Int): String = buildJsonObject {
        put("ids", buildJsonArray { removedIds.forEach { add(JsonPrimitive(it)) } })
        put("total", total)
    }.toString()

    fun agentRowsJson(
        entries: List<EntryDTO>,
        titles: Map<String, String> = emptyMap(),
        running: Set<String> = emptySet(),
        expanded: Boolean = false,
        ownerRunning: Boolean = false,
    ): List<String> = agentRows(entries, titles, running, expanded, ownerRunning).map { it.toString() }

    private fun agentRows(
        entries: List<EntryDTO>,
        titles: Map<String, String>,
        running: Set<String>,
        expanded: Boolean,
        ownerRunning: Boolean,
    ): List<JsonObject> =
        entries.mapIndexed { index, dto ->
            buildJsonObject {
                put("id", index.toLong())
                put("order", index)
                put("speaker", dto.speaker)
                put("text", dto.text)
                dto.meta?.let { put("meta", it) }
                dto.toolUseId?.let { id -> titles[id]?.let { put("title", it) } }
                dto.toolUseId?.let { put("toolUseId", it) }
                dto.filePath?.let { put("filePath", it) }
                dto.commandText?.let { put("command", it) }
                dto.messageText?.let { put("message", it) }
                guardFields(dto.blockedRule, dto.bypassedRule, dto.bypassAction)
                put("state", agentRowState(dto, running, ownerRunning))
                if (expanded) put("open", true)
                put("elapsed", 0)
                reviewableFlag(
                    dto.speaker == Speaker.TOOL.name && dto.toolUseId != null && dto.meta in DiffPresenter.REVIEWABLE_TOOLS,
                )
            }
        }

    private fun agentRowState(dto: EntryDTO, running: Set<String>, ownerRunning: Boolean): String = when {
        dto.failed -> "ERROR"
        dto.toolUseId in running -> "RUNNING"
        dto.inFlight -> if (ownerRunning) "RUNNING" else "ERROR"
        else -> "FINISHED"
    }

    fun linksJson(rowId: Long, resolved: List<dev.lain.claudejb.controller.context.LinkResolver.Resolved>): String =
        buildJsonObject {
            put("rowId", rowId)
            put(
                "links",
                buildJsonArray {
                    resolved.forEach { r ->
                        add(
                            buildJsonObject {
                                put("token", r.token)
                                put("path", r.path)
                                r.line?.let { put("line", it) }
                            },
                        )
                    }
                },
            )
        }.toString()
}
