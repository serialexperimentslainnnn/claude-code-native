package dev.lain.claudejb.controller.session.events

import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.diff.DiffPresenter
import dev.lain.claudejb.model.mcp.Batch
import dev.lain.claudejb.model.mcp.OwnTools
import dev.lain.claudejb.model.permission.scan.ToolInputScanner
import dev.lain.claudejb.model.protocol.ClaudeEvent
import dev.lain.claudejb.model.session.agents.AgentStatus
import dev.lain.claudejb.model.session.transcript.CardPlaces
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.session.transcript.ToolNaming
import dev.lain.claudejb.model.session.transcript.ToolState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

class ToolEvents(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
) {

    private class Item(val id: String, val args: JsonObject, val review: OwnTools.Review?)

    private class Own(val call: OwnTools.Call, val items: List<Item>, val batch: Boolean)

    private val liveOutput = LiveToolOutput(s, edt)
    private val reviews = EditReviews(s, edt)
    private val ownCalls = HashMap<String, Own>()

    fun onToolUse(event: ClaudeEvent.ToolUse) {
        val own = OwnTools.parse(event.name, event.input)?.let { plan(it, event) }
        val reviewable = own == null && event.name in DiffPresenter.REVIEWABLE_TOOLS
        if (reviewable) reviews.capture(event.name, event.input, event.id)
        own?.items?.forEach { item -> item.review?.let { reviews.capture(it, item.id) } }
        edt {
            if (event.parentToolUseId == null) s.reconciler.onMessageBoundary()
            if (own != null) {
                ownCalls[event.id] = own
                own.items.forEach { ownRow(it, event) }
                return@edt
            }
            s.transcript.add(
                Speaker.TOOL,
                ToolNaming.formatToolUse(event.name, event.input, s.project.basePath),
                meta = event.name,
                toolUseId = event.id,
                parentToolUseId = event.parentToolUseId,
                toolState = ToolState.LOADING,
                filePath = ToolNaming.toolFilePath(event.name, event.input, s.project.basePath),
                commandText = ToolInputScanner.commandText(event.input),
                messageText = ToolInputScanner.messageText(event.input),
                reviewable = reviewable,
            )
            liveOutput.flushEarly(event.id)
            if (reviewable) s.prompts.bindTool(event.id)
        }
    }

    private fun plan(own: OwnTools.Call, event: ClaudeEvent.ToolUse): Own {
        val args = OwnTools.argsOf(event.input)
        val split = Batch.split(own.argument, args)
        val items = (split ?: listOf(args)).mapIndexed { index, itemArgs ->
            val id = if (split == null) event.id else Batch.itemId(event.id, index)
            Item(id, itemArgs, OwnTools.reviewAs(own, itemArgs, s.project.basePath))
        }
        return Own(own, items, split != null)
    }

    private fun ownRow(item: Item, event: ClaudeEvent.ToolUse) {
        val call = ownCalls[event.id]?.call ?: return
        s.transcript.add(
            Speaker.TOOL,
            OwnTools.label(call, item.args),
            meta = event.name,
            toolUseId = item.id,
            parentToolUseId = event.parentToolUseId,
            toolState = ToolState.LOADING,
            filePath = OwnTools.path(item.args),
            commandText = OwnTools.command(call, item.args),
            messageText = if (item.review == null) OwnTools.detailsToon(call, item.args) else null,
            reviewable = item.review != null,
        )
        liveOutput.flushEarly(item.id)
    }

    fun onToolResult(event: ClaudeEvent.ToolResult) = edt {
        if (s.backgroundTaskRegistry.observe(event)) {
            s.poll.ensureOutputTail()
            fireState()
        }
        val own = ownCalls.remove(event.toolUseId)
        if (!event.isError) refreshAfter(event.toolUseId, own)
        if (own != null) {
            ownResult(event, own)
            return@edt
        }
        settle(event.toolUseId, event.isError)
        val text = event.content.trim()
        reviews.settle(event.toolUseId) { diff ->
            if (diff != null) {
                s.transcript.addToolOutput(event.toolUseId, diff, meta = DIFF)
            } else {
                rawOutput(event.toolUseId, text, event.isError)
            }
        }
    }

    private fun refreshAfter(toolUseId: String, own: Own?) {
        s.diffs.refreshTouched()
        if (own == null && ToolNaming.mayHaveWrittenUnknownFiles(s.transcript.toolNameOf(toolUseId))) {
            s.diffs.refreshProjectTree()
        }
    }

    private fun settle(toolUseId: String, failed: Boolean) {
        liveOutput.settle(toolUseId)
        if (s.runningAgents.nodes.values.none { it.meta.toolUseId == toolUseId }) {
            s.transcript.setToolState(toolUseId, if (failed) ToolState.ERROR else ToolState.FINISHED)
        }
    }

    private fun ownResult(event: ClaudeEvent.ToolResult, own: Own) {
        val text = event.content.trim()
        val decoded = if (event.isError) null else OwnTools.decodeResult(text) as? JsonObject
        val results: List<JsonObject?> = if (own.batch) batchResults(decoded, own.items.size) else listOf(decoded)
        own.items.forEachIndexed { index, item ->
            val result = results.getOrNull(index)
            val error = result?.let { errorOf(it) }
            settle(item.id, event.isError || result == null || error != null)
            when {
                result == null -> {
                    reviews.forget(item.id)
                    if (index == 0) rawOutput(item.id, text, event.isError)
                }

                error != null -> {
                    reviews.forget(item.id)
                    s.transcript.addToolOutput(item.id, error, meta = ERROR)
                }

                else -> reviews.settle(item.id) { diff ->
                    if (diff != null) s.transcript.addToolOutput(item.id, diff, meta = DIFF) else ownOutput(own.call, item, result)
                }
            }
        }
    }

    private fun batchResults(decoded: JsonObject?, count: Int): List<JsonObject?> {
        val rows = (decoded?.get("items") as? JsonArray)?.map { it as? JsonObject }.orEmpty()
        if (decoded?.get(CLEAN) !is JsonArray) return rows
        val byIndex = rows.filterNotNull().associateBy { (it[INDEX] as? JsonPrimitive)?.intOrNull }
        return List(count) { index -> byIndex[index]?.let { JsonObject(it - INDEX) } ?: JsonObject(emptyMap()) }
    }

    private fun ownOutput(call: OwnTools.Call, item: Item, result: JsonObject) {
        s.transcript.setToolPlaces(item.id, CardPlaces.of(call.argument ?: "", item.args, result))
        if (result.isEmpty()) return
        val read = if (OwnTools.isRead(call)) OwnTools.readText(result) else null
        if (read != null) {
            s.transcript.addToolOutput(item.id, read)
        } else {
            s.transcript.addToolOutput(item.id, result.toString(), meta = TOON)
        }
    }

    private fun rawOutput(toolUseId: String, text: String, failed: Boolean) {
        if (text.isBlank()) return
        val tags = buildList {
            if (s.transcript.isCommandCall(toolUseId)) add("command")
            if (failed) add(ERROR)
        }
        s.transcript.addToolOutput(toolUseId, text, meta = tags.joinToString(" ").ifBlank { null })
    }

    private fun errorOf(result: JsonObject): String? = (result[ERROR] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val TOON = "toon"
        const val DIFF = "diff"
        const val ERROR = "error"
        const val CLEAN = "clean"
        const val INDEX = "index"
    }

    fun labelAgentCards() {
        s.runningAgents.nodes.values.forEach { node ->
            val toolUseId = node.meta.toolUseId ?: return@forEach
            s.transcript.toolNameOf(toolUseId) ?: return@forEach
            s.transcript.setToolState(
                toolUseId,
                when (node.status) {
                    AgentStatus.RUNNING -> ToolState.RUNNING
                    AgentStatus.COMPLETED -> ToolState.FINISHED
                    else -> ToolState.ERROR
                },
            )
            val label = node.meta.description?.takeIf { it.isNotBlank() } ?: return@forEach
            s.transcript.setToolTitle(toolUseId, "${node.kindLabel} ($label)")
        }
    }
}
