package dev.lain.claudejb.view.feed

import dev.lain.claudejb.controller.session.AttentionLanding
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.controller.session.guard.GuardRestore
import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.model.session.agents.AgentStatus
import dev.lain.claudejb.model.session.transcript.EntryDTO
import dev.lain.claudejb.model.session.transcript.TranscriptEntry
import dev.lain.claudejb.model.session.transcript.TranscriptModel
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.view.payload.chat.JcefTranscriptPayload
import dev.lain.claudejb.view.window.PushStream.Companion.NO_ARGS
import kotlinx.serialization.json.JsonArray
import javax.swing.Timer

internal class ChatTranscriptView(
    private val session: ClaudeSession,
    private val emit: (PagePush) -> Unit,
) : TranscriptModel.Listener {

    private sealed interface Shown {
        object Chat : Shown
        data class Agent(val id: String) : Shown
        data class Task(val id: String) : Shown
    }

    private val dirty = LinkedHashSet<Long>()
    private var structural = false
    private val timer = Timer(ELAPSED_TICK_MS) { onTick() }.apply { isRepeats = true }
    private val deltas = TranscriptDeltas()
    private val appended = LinkedHashMap<Long, Pair<TranscriptEntry, StringBuilder>>()

    private var shown: Shown = Shown.Chat

    private var lastRows: List<String> = emptyList()

    private var lastTask: Pair<Int, Boolean>? = null

    val showsTask: Boolean get() = shown is Shown.Task

    val showsChat: Boolean get() = shown is Shown.Chat

    fun showTranscript(agentId: String?) {
        show(agentId?.let { Shown.Agent(it) } ?: Shown.Chat)
    }

    fun showBackgroundTask(taskId: String) = show(Shown.Task(taskId))

    private fun show(next: Shown) {
        exec("closeDashboard", NO_ARGS)
        if (shown == next) return
        shown = next
        resync()
    }

    fun resync() {
        dirty.clear()
        appended.clear()
        deltas.reset()
        lastRows = emptyList()
        lastTask = null
        exec("clear", NO_ARGS)
        when (val current = shown) {
            is Shown.Chat -> {
                exec("clearAgentSelection", NO_ARGS)
                fullResync()
                trimNotice(emptyList(), session.transcript.trimmedCount)
            }

            is Shown.Agent -> {
                exec("revealAgentTab", JcefBridge.jsString(current.id))
                pushEntries(agentEntries(current.id))
            }

            is Shown.Task -> {
                exec("revealTaskTab", JcefBridge.jsString(current.id))
                refreshTask(current.id)
            }
        }
    }

    fun refreshShown() {
        when (val current = shown) {
            is Shown.Chat -> Unit
            is Shown.Agent -> pushEntries(agentEntries(current.id))
            is Shown.Task -> refreshTask(current.id)
        }
    }

    private fun refreshTask(taskId: String) {
        val task = session.backgroundTaskRegistry.taskOf(taskId)
        val signature = task?.let { it.output.length to it.running }
        if (signature != null && signature == lastTask) return
        lastTask = signature
        pushEntries(BackgroundTaskView.entries(session, taskId), expanded = true)
    }

    private fun agentEntries(agentId: String): List<EntryDTO> {
        val entries = session.runningAgents.nodes[agentId]?.entries.orEmpty()
        return GuardRestore.reinstate(entries, session.guard.alertsAnchoredIn(entries))
    }

    fun shows(landing: AttentionLanding): Boolean = when (landing) {
        is AttentionLanding.Chat -> showsChat
        is AttentionLanding.Agent -> shown == Shown.Agent(landing.agentId)
        is AttentionLanding.Elsewhere -> false
    }

    private fun pushEntries(entries: List<EntryDTO>, expanded: Boolean = false) {
        val titles = HashMap<String, String>()
        val running = HashSet<String>()
        session.runningAgents.nodes.values.forEach { node ->
            val tool = node.meta.toolUseId ?: return@forEach
            node.meta.description?.takeIf { it.isNotBlank() }?.let { titles[tool] = "${node.kindLabel} ($it)" }
            if (node.status == AgentStatus.RUNNING) running += tool
        }
        val ownerRunning = when (val current = shown) {
            is Shown.Agent -> session.runningAgents.nodes[current.id]?.status == AgentStatus.RUNNING
            is Shown.Task -> session.backgroundTaskRegistry.all.firstOrNull { it.taskId == current.id }?.running == true
            else -> false
        }
        val rows = JcefTranscriptPayload.agentRowsJson(entries, titles, running, expanded, ownerRunning)
        if (rows == lastRows) return
        if (rows.size < lastRows.size) {
            lastRows = rows
            exec("clear", NO_ARGS)
            if (rows.isNotEmpty()) exec("batch", "[${rows.joinToString(",")}]")
            return
        }
        val changed = rows.filterIndexed { index, row -> index >= lastRows.size || lastRows[index] != row }
        lastRows = rows
        if (changed.isNotEmpty()) exec("batch", "[${changed.joinToString(",")}]")
    }

    override fun onAdded(entry: TranscriptEntry, index: Int) {
        if (index < session.transcript.entries.size - 1) structural = true
        dirty.add(entry.id)
        ensureTimer()
    }

    override fun onUpdated(entry: TranscriptEntry) {
        dirty.add(entry.id)
        appended.remove(entry.id)
        ensureTimer()
    }

    override fun onAppended(entry: TranscriptEntry, delta: String) {
        if (entry.id !in dirty) appended.getOrPut(entry.id) { entry to StringBuilder() }.second.append(delta)
        ensureTimer()
    }

    override fun onCleared() {
        dirty.clear()
        appended.clear()
        structural = false
        deltas.reset()
        exec("clear", NO_ARGS)
    }

    override fun onTrimmed(removedIds: List<Long>, totalTrimmed: Int) {
        dirty.removeAll(removedIds.toSet())
        appended.keys.removeAll(removedIds.toSet())
        deltas.forget(removedIds)
        if (shown == Shown.Chat) trimNotice(removedIds, totalTrimmed)
    }

    private fun trimNotice(removedIds: List<Long>, totalTrimmed: Int) =
        exec("trimRows", JcefTranscriptPayload.trimJson(removedIds, totalTrimmed))

    private fun ensureTimer() {
        if (!timer.isRunning) timer.start()
    }

    private fun onTick() {
        if (shown != Shown.Chat) {
            dirty.clear()
            appended.clear()
            structural = true
            timer.stop()
            return
        }
        val entries = session.transcript.entries
        val items: List<Pair<TranscriptEntry, Int>> = if (structural) {
            structural = false
            appended.clear()
            entries.mapIndexed { index, entry -> entry to index }
        } else {
            val idToIndex = HashMap<Long, Int>(entries.size)
            entries.forEachIndexed { index, entry -> idToIndex[entry.id] = index }
            dirty.mapNotNull { id -> idToIndex[id]?.let { entries[it] to it } }
        }
        dirty.clear()
        val split = deltas.split(items)
        if (split.rows.isNotEmpty()) exec("batch", JsonArray(split.rows).toString())
        split.appends.forEach { exec("append", JcefTranscriptPayload.appendJson(it.id, it.delta)) }
        flushAppends()
        if (!structural && dirty.isEmpty()) timer.stop()
    }

    private fun flushAppends() {
        val grown = appended.values.toList()
        appended.clear()
        for ((entry, delta) in grown) {
            val text = delta.toString()
            if (deltas.grew(entry, text)) exec("append", JcefTranscriptPayload.appendJson(entry.id, text)) else dirty.add(entry.id)
        }
    }

    fun fullResync() {
        structural = true
        ensureTimer()
    }

    fun stop() = timer.stop()

    private fun exec(method: String, json: String) = emit(PagePush(method, json))

    private companion object {
        const val ELAPSED_TICK_MS = 30
    }
}
