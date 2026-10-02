package dev.lain.claudejb.controller.session.events

import dev.lain.claudejb.controller.mcp.ToolOutput
import dev.lain.claudejb.controller.mcp.ToolOutputListener
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.session.transcript.LiveLines
import dev.lain.claudejb.model.session.transcript.TranscriptEntry

class LiveToolOutput(private val s: ClaudeSession, private val edt: (() -> Unit) -> Unit) {

    private val lock = Any()
    private val pending = LinkedHashMap<String, ArrayDeque<String>>()
    private var flushQueued = false

    private val live = HashMap<String, Pair<TranscriptEntry, LiveLines>>()
    private val early = bounded<LiveLines>(MAX_EARLY_TOOLS)
    private val settled = bounded<Unit>(MAX_SETTLED_TOOLS)

    init {
        s.project.messageBus.connect(s).subscribe(
            ToolOutput.TOPIC,
            ToolOutputListener { toolUseId, line -> offer(toolUseId, line) },
        )
    }

    fun offer(toolUseId: String, line: String) {
        val schedule = synchronized(lock) {
            val lines = pending.getOrPut(toolUseId) { ArrayDeque() }
            lines.addLast(line)
            if (lines.size > MAX_PENDING_LINES) lines.removeFirst()
            !flushQueued.also { flushQueued = true }
        }
        if (schedule) edt { flush() }
    }

    private fun flush() {
        val batch = synchronized(lock) {
            flushQueued = false
            LinkedHashMap(pending).also { pending.clear() }
        }
        batch.forEach { (toolUseId, lines) -> absorb(toolUseId, lines) }
    }

    private fun absorb(toolUseId: String, lines: Collection<String>) {
        if (toolUseId in settled) return
        if (!s.transcript.knowsTool(toolUseId)) {
            val ring = early.getOrPut(toolUseId) { LiveLines() }
            lines.forEach(ring::add)
            return
        }
        val (entry, ring) = live.getOrPut(toolUseId) {
            s.transcript.addToolOutput(toolUseId, "", meta = LIVE) to (early.remove(toolUseId) ?: LiveLines())
        }
        lines.forEach(ring::add)
        s.transcript.replaceText(entry, ring.render())
    }

    fun flushEarly(toolUseId: String) {
        val ring = early.remove(toolUseId) ?: return
        val entry = s.transcript.addToolOutput(toolUseId, ring.render(), meta = LIVE)
        live[toolUseId] = entry to ring
    }

    fun settle(toolUseId: String) {
        val rest = synchronized(lock) { pending.remove(toolUseId) }
        if (rest != null) absorb(toolUseId, rest)
        live.remove(toolUseId)
        early.remove(toolUseId)
        settled[toolUseId] = Unit
    }

    private companion object {
        const val LIVE = "live"
        const val MAX_PENDING_LINES = 400
        const val MAX_EARLY_TOOLS = 64
        const val MAX_SETTLED_TOOLS = 256

        fun <V> bounded(cap: Int): LinkedHashMap<String, V> = object : LinkedHashMap<String, V>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean = size > cap
        }
    }
}
