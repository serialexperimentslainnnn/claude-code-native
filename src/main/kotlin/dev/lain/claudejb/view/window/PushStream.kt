package dev.lain.claudejb.view.window

import dev.lain.claudejb.rpc.FrontendChannel
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.util.logger

internal interface PushSink {
    fun push(push: PagePush)

    fun close()
}

internal class PushStream(
    private val cap: Int = DEFAULT_CAP,
    private val onStale: () -> Unit,
) {

    enum class Kind { SNAPSHOT, TRANSCRIPT, TRANSIENT }

    private val lock = Any()
    private val snapshots = LinkedHashMap<String, String>()
    private val deltas = ArrayDeque<PagePush>()
    private val pending = ArrayDeque<PagePush>()
    private val sinks = ArrayList<PushSink>()
    private var stale = false
    private var closed = false

    fun emit(push: PagePush) {
        synchronized(lock) {
            if (closed) return
            record(push)
            sinks.forEach { it.push(push) }
        }
    }

    fun hasSinks(): Boolean = synchronized(lock) { sinks.isNotEmpty() }

    fun attach(sink: PushSink): () -> Unit {
        val resync = synchronized(lock) {
            if (closed) {
                sink.close()
                return {}
            }
            sinks += sink
            val needsResync = replayTo(sink)
            while (pending.isNotEmpty()) sink.push(pending.removeFirst())
            needsResync
        }
        if (resync) onStale()
        return { synchronized(lock) { sinks -= sink } }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            sinks.forEach(PushSink::close)
            sinks.clear()
            snapshots.clear()
            deltas.clear()
            pending.clear()
        }
    }

    private fun replayTo(sink: PushSink): Boolean {
        snapshots.forEach { (method, json) -> sink.push(PagePush(method, json)) }
        if (stale) return true
        sink.push(CLEAR)
        deltas.forEach(sink::push)
        return false
    }

    private fun record(push: PagePush) {
        when (kindOf(push.method)) {
            Kind.SNAPSHOT -> snapshots[push.method] = push.json
            Kind.TRANSCRIPT -> log(push)
            Kind.TRANSIENT -> if (sinks.isEmpty()) hold(push)
        }
    }

    private fun log(push: PagePush) {
        if (push.method == CLEAR.method) {
            deltas.clear()
            stale = false
            return
        }
        if (stale) return
        if (deltas.size >= cap) {
            deltas.clear()
            stale = true
            return
        }
        deltas.addLast(push)
    }

    private fun hold(push: PagePush) {
        if (FrontendChannel.isFrontendPush(push.method) && push.method != FrontendChannel.FOCUS) {
            LOG.warn("Claude Code dropped '${push.method}': no chat window is listening, and it must not run later")
            return
        }
        if (pending.size >= cap) pending.removeFirst()
        pending.addLast(push)
    }

    companion object {
        const val NO_ARGS = "null"

        const val DEFAULT_CAP = 4096

        private val LOG = logger<PushStream>()

        private val CLEAR = PagePush("clear", NO_ARGS)

        val TRANSCRIPT_METHODS: Set<String> = setOf(
            "clear",
            "batch",
            "append",
            "trimRows",
            "revealAgentTab",
            "revealTaskTab",
            "clearAgentSelection",
        )

        fun kindOf(method: String): Kind = when (method) {
            in FrontendChannel.SNAPSHOT_METHODS -> Kind.SNAPSHOT
            in TRANSCRIPT_METHODS -> Kind.TRANSCRIPT
            else -> Kind.TRANSIENT
        }
    }
}
