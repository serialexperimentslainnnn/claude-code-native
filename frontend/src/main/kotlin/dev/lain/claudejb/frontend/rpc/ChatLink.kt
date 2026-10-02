package dev.lain.claudejb.frontend.rpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.project.ProjectId
import dev.lain.claudejb.rpc.ChatApi
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.PagePush
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class ChatLink internal constructor(
    private val scope: CoroutineScope,
    private val projectId: ProjectId,
    private val chatId: ChatId,
    private val onPush: (PagePush) -> Unit,
) : Disposable {

    private val outgoing = Channel<String>(Channel.UNLIMITED)

    private val sending = scope.launch { for (json in outgoing) deliver(json) }

    private val lock = Any()

    private var disposed = false

    private var receiving: Job = scope.launch { receive() }

    fun post(json: String) {
        outgoing.trySend(json)
    }

    fun ready() {
        synchronized(lock) {
            if (disposed) return
            receiving.cancel()
            receiving = scope.launch { receive() }
        }
    }

    override fun dispose() {
        synchronized(lock) {
            disposed = true
            outgoing.close()
            sending.cancel()
            receiving.cancel()
        }
    }

    private suspend fun deliver(json: String) {
        runCatching { ChatApi.getInstance().post(projectId, chatId, json) }.onFailure { cause ->
            if (cause !is Exception || cause is CancellationException) throw cause
            log.warn("Claude Code could not hand a chat page message to the host", cause)
        }
    }

    private suspend fun receive() {
        durable { ChatApi.getInstance().pushes(projectId, chatId).collect { onPush(it) } }
    }

    private companion object {
        private val log = logger<ChatLink>()
    }
}
