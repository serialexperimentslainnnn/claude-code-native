package dev.lain.claudejb.model.session.transcript

import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CopyOnWriteArrayList

enum class Speaker { USER, ASSISTANT, THINKING, TOOL, TOOL_OUTPUT, SYSTEM, ERROR, MEMORY }

enum class ToolState { LOADING, RUNNING, FINISHED, ERROR }

class TranscriptEntry(
    val id: Long,
    val speaker: Speaker,
    text: String,
    val meta: String? = null,
    val toolUseId: String? = null,
    val parentToolUseId: String? = null,
    toolState: ToolState = ToolState.FINISHED,
    val filePath: String? = null,
    val commandText: String? = null,
    val messageText: String? = null,
    val blockedRule: String? = null,
    val bypassedRule: String? = null,
    val bypassAction: String? = null,
    val reviewable: Boolean = false,
) {
    private var settled: String? = text

    private var growing: StringBuilder? = null

    var text: String
        get() = settled ?: growing.toString().also { settled = it }
        internal set(value) {
            settled = value
            growing = null
        }

    internal fun grow(delta: String) {
        val builder = growing ?: StringBuilder(settled.orEmpty()).also { growing = it }
        builder.append(delta)
        settled = null
    }

    var toolState: ToolState = toolState
        internal set

    var elapsedSeconds: Double = 0.0
        internal set

    var toolTitle: String? = null
        internal set

    var places: List<CardPlace> = emptyList()
        internal set

    var trimmed: Boolean = false
        internal set
}

class TranscriptModel {

    companion object {
        const val MAX_ENTRIES = 500
    }

    interface Listener {
        fun onAdded(entry: TranscriptEntry, index: Int) {}
        fun onUpdated(entry: TranscriptEntry) {}

        fun onAppended(entry: TranscriptEntry, delta: String) = onUpdated(entry)
        fun onCleared() {}

        fun onTrimmed(removedIds: List<Long>, totalTrimmed: Int) {}
    }

    private val backing = ArrayList<TranscriptEntry>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private var nextId = 0L

    private val byToolUseId = HashMap<String, TranscriptEntry>()
    private val parentOf = HashMap<String, String>()

    private var indexById: HashMap<Long, Int>? = HashMap()

    val entries: List<TranscriptEntry> get() = backing

    var trimmedCount: Int = 0
        private set

    fun addListener(listener: Listener) = listeners.add(listener)
    fun removeListener(listener: Listener) = listeners.remove(listener)

    @TestOnly
    fun parentToolOf(toolUseId: String): String? = parentOf[toolUseId]

    fun knowsTool(toolUseId: String): Boolean = byToolUseId.containsKey(toolUseId)

    fun add(
        speaker: Speaker,
        text: String,
        meta: String? = null,
        toolUseId: String? = null,
        parentToolUseId: String? = null,
        toolState: ToolState = ToolState.FINISHED,
        filePath: String? = null,
        commandText: String? = null,
        messageText: String? = null,
        blockedRule: String? = null,
        bypassedRule: String? = null,
        bypassAction: String? = null,
        reviewable: Boolean = false,
    ): TranscriptEntry {
        val entry = TranscriptEntry(
            nextId++, speaker, text, meta, toolUseId, parentToolUseId, toolState, filePath, commandText,
            messageText, blockedRule, bypassedRule, bypassAction, reviewable,
        )
        if (speaker == Speaker.TOOL && toolUseId != null) {
            byToolUseId[toolUseId] = entry
            if (parentToolUseId != null) parentOf[toolUseId] = parentToolUseId else parentOf.remove(toolUseId)
        }
        val index = insertionIndexFor(parentToolUseId)
        place(entry, index)
        listeners.forEach { it.onAdded(entry, index) }
        trimToCap()
        return entry
    }

    private fun trimToCap() {
        if (backing.size <= MAX_ENTRIES) return
        val removedIds = ArrayList<Long>(backing.size - MAX_ENTRIES)
        while (backing.size > MAX_ENTRIES) {
            val removed = backing.removeAt(0)
            removed.trimmed = true
            removedIds += removed.id
            val toolUseId = removed.toolUseId
            if (toolUseId != null && byToolUseId[toolUseId] === removed) {
                byToolUseId.remove(toolUseId)
                parentOf.remove(toolUseId)
            }
        }
        indexById = null
        trimmedCount += removedIds.size
        listeners.forEach { it.onTrimmed(removedIds, trimmedCount) }
    }

    fun addToolOutput(toolUseId: String, text: String, parentToolUseId: String? = null, meta: String? = null): TranscriptEntry {
        val toolEntry = byToolUseId[toolUseId]
        val toolIdx = if (toolEntry != null) indexOf(toolEntry.id) else -1
        val parent = parentToolUseId ?: toolEntry?.parentToolUseId
        val insertAt = if (toolIdx < 0) {
            backing.size
        } else {
            var i = toolIdx + 1
            while (i < backing.size && backing[i].speaker == Speaker.TOOL_OUTPUT && backing[i].toolUseId == toolUseId) i++
            i
        }
        val entry = TranscriptEntry(nextId++, Speaker.TOOL_OUTPUT, text, meta, toolUseId, parent)
        place(entry, insertAt)
        listeners.forEach { it.onAdded(entry, insertAt) }
        trimToCap()
        return entry
    }

    private fun isDescendantOf(child: String?, ancestor: String): Boolean {
        var cur = child
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur)) {
            if (cur == ancestor) return true
            cur = parentOf[cur]
        }
        return false
    }

    private fun belongsToSubtree(e: TranscriptEntry, parent: String): Boolean =
        e.toolUseId == parent || isDescendantOf(e.toolUseId, parent) || isDescendantOf(e.parentToolUseId, parent)

    private fun insertionIndexFor(parent: String?): Int {
        if (parent == null) return backing.size
        val parentEntry = byToolUseId[parent] ?: return backing.size
        val anchor = indexOf(parentEntry.id)
        if (anchor < 0) return backing.size
        var i = anchor + 1
        while (i < backing.size && belongsToSubtree(backing[i], parent)) i++
        return i
    }

    private fun place(entry: TranscriptEntry, index: Int) {
        backing.add(index, entry)
        val cache = indexById ?: return
        if (index == backing.size - 1) cache[entry.id] = index else indexById = null
    }

    fun indexOf(entryId: Long): Int {
        val cache = indexById ?: HashMap<Long, Int>(backing.size * 2).also { rebuilt ->
            backing.forEachIndexed { i, e -> rebuilt[e.id] = i }
            indexById = rebuilt
        }
        return cache[entryId] ?: -1
    }

    fun append(entry: TranscriptEntry, delta: String) {
        entry.grow(delta)
        listeners.forEach { it.onAppended(entry, delta) }
    }

    fun replaceText(entry: TranscriptEntry, text: String) {
        entry.text = text
        listeners.forEach { it.onUpdated(entry) }
    }

    fun toolNameOf(toolUseId: String): String? = byToolUseId[toolUseId]?.meta

    fun commandTextOf(toolUseId: String): String? = byToolUseId[toolUseId]?.commandText

    fun isCommandCall(toolUseId: String): Boolean = commandTextOf(toolUseId) != null

    fun setToolState(toolUseId: String, state: ToolState, elapsedSeconds: Double? = null) {
        val entry = byToolUseId[toolUseId] ?: return
        entry.toolState = state
        if (elapsedSeconds != null) entry.elapsedSeconds = elapsedSeconds
        listeners.forEach { it.onUpdated(entry) }
    }

    fun setToolPlaces(toolUseId: String, places: List<CardPlace>) {
        val entry = byToolUseId[toolUseId] ?: return
        if (entry.places == places) return
        entry.places = places
        listeners.forEach { it.onUpdated(entry) }
    }

    fun setToolTitle(toolUseId: String, title: String): Boolean {
        val entry = byToolUseId[toolUseId] ?: return false
        if (entry.toolTitle == title) return false
        entry.toolTitle = title
        listeners.forEach { it.onUpdated(entry) }
        return true
    }

    fun clear() {
        backing.clear()
        indexById = HashMap()
        byToolUseId.clear()
        parentOf.clear()
        trimmedCount = 0
        listeners.forEach { it.onCleared() }
    }
}
