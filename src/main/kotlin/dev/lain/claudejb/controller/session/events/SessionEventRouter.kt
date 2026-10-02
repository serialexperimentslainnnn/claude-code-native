package dev.lain.claudejb.controller.session.events

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import dev.lain.claudejb.controller.session.AttentionReason
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.protocol.ClaudeEvent
import dev.lain.claudejb.model.session.turn.StreamBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SessionEventRouter(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    fireState: () -> Unit,
    fireMetadata: () -> Unit,
    fireAttention: (AttentionReason) -> Unit,
    private val notices: NoticeNarrator,
) {

    private val stream = StreamBuffer()
    private val flushLock = Any()
    private val drainScheduled = AtomicBoolean(false)

    val toolEvents = ToolEvents(s, edt, fireState)
    private val taskEvents = TaskEvents(s, edt, fireState)
    private val signalEvents = SignalEvents(s, edt, fireState, fireMetadata)
    private val controlEvents = ControlEvents(s, edt)
    val conversation = ConversationEvents(s, s.project, edt, fireState, fireAttention)

    fun onEvent(event: ClaudeEvent) {
        if (event is ClaudeEvent.Stream) {
            stream.buffer(event)
            if (event == ClaudeEvent.BlockStop) flushDeltas() else scheduleDrain()
            return
        }
        flushDeltas()
        when (event) {
            is ClaudeEvent.Conversation -> conversation.onConversation(event)
            is ClaudeEvent.Control -> controlEvents.onControl(event)
            is ClaudeEvent.Task -> taskEvents.onTask(event)
            is ClaudeEvent.SessionSignal -> signalEvents.onSessionSignal(event)
            is ClaudeEvent.HookTelemetry -> controlEvents.onHookTelemetry(event)
            is ClaudeEvent.Notice -> notices.onNotice(event)
            is ClaudeEvent.Stream -> {}
        }
    }

    fun flushDeltas() {
        flush(null)
    }

    private fun scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return
        AppExecutorUtil.getAppScheduledExecutorService().schedule({ drainOnTimer() }, DRAIN_MS, TimeUnit.MILLISECONDS)
    }

    private fun drainOnTimer() {
        if (!flush(::drainSettled)) drainSettled()
    }

    private fun drainSettled() {
        drainScheduled.set(false)
        if (stream.hasPending()) scheduleDrain()
    }

    private fun flush(then: (() -> Unit)?): Boolean {
        val onEdt = ApplicationManager.getApplication().isDispatchThread
        val drained = synchronized(flushLock) {
            val drained = stream.drain() ?: return false
            if (!onEdt) {
                edt {
                    apply(drained)
                    then?.invoke()
                }
            }
            drained
        }
        if (onEdt) {
            apply(drained)
            then?.invoke()
        }
        return true
    }

    private fun apply(drained: StreamBuffer.Drained) {
        for ((isThinking, text) in drained.runs) {
            if (isThinking) s.reconciler.appendThinking(text) else s.reconciler.appendAssistant(text)
        }
        drained.usage?.let { s.tokens.onLiveUsage(it[0], it[1], it[2], it[3]) }
    }

    private companion object {
        const val DRAIN_MS = 40L
    }
}
