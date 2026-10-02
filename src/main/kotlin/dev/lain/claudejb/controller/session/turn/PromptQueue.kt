package dev.lain.claudejb.controller.session.turn

import com.intellij.util.concurrency.AppExecutorUtil
import dev.lain.claudejb.model.protocol.control.ControlProtocol
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.session.transcript.TranscriptModel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PromptQueue(
    private val transcript: TranscriptModel,
    private val edt: (() -> Unit) -> Unit,
    private val write: (String) -> Boolean,
    private val canSend: () -> Boolean,
    private val onSent: () -> Unit,
    private val fireState: () -> Unit,
    private val offload: (Runnable) -> Unit = AppExecutorUtil.createBoundedApplicationPoolExecutor(WRITER, 1)::execute,
) {

    private data class Outgoing(val text: String, val images: List<Pair<String, String>>, val displayText: String)

    private val queue = ArrayDeque<Outgoing>()

    private var inFlight: Outgoing? = null

    private val toolUseTurn = ConcurrentHashMap<String, String>()

    @Volatile var currentUserMessageId: String? = null
        private set

    @Volatile var suggestion: String? = null
        private set

    fun queued(): List<String> = listOfNotNull(inFlight?.displayText) + queue.map { it.displayText }

    fun userMessageIdFor(toolUseId: String): String? = toolUseTurn[toolUseId]

    fun bindTool(toolUseId: String) {
        currentUserMessageId?.let { toolUseTurn[toolUseId] = it }
    }

    fun enqueue(text: String, images: List<Pair<String, String>>, displayText: String) = edt {
        queue.addLast(Outgoing(text, images, displayText))
        fireState()
        pump()
    }

    fun remove(index: Int) = edt {
        val at = if (inFlight != null) index - 1 else index
        if (at in queue.indices) {
            queue.removeAt(at)
            fireState()
        }
    }

    fun pump() {
        if (inFlight != null || !canSend() || queue.isEmpty()) return
        val next = queue.removeFirst()
        inFlight = next
        val msgUuid = UUID.randomUUID().toString()
        offload(
            Runnable {
                val sent = write(ControlProtocol.userMessageWithImages(next.text, next.images, uuid = msgUuid))
                edt { settle(next, msgUuid, sent) }
            },
        )
    }

    private fun settle(next: Outgoing, msgUuid: String, sent: Boolean) {
        inFlight = null
        if (!sent) {
            queue.addFirst(next)
            return
        }
        transcript.add(Speaker.USER, next.displayText)
        currentUserMessageId = msgUuid
        onSent()
        dropSuggestion()
        fireState()
        pump()
    }

    fun dropSuggestion() {
        suggestion = null
    }

    fun suggest(text: String) {
        suggestion = text.takeIf { it.isNotBlank() }
        edt { fireState() }
    }

    fun clearSuggestion() {
        if (suggestion == null) return
        suggestion = null
        edt { fireState() }
    }

    fun clear() = queue.clear()

    fun forgetTurn() {
        toolUseTurn.clear()
        currentUserMessageId = null
    }

    private companion object {
        const val WRITER = "Claude Code prompt writer"
    }
}
