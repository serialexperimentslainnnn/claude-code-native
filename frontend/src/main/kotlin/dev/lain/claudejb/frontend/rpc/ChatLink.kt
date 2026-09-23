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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ChatLink internal constructor(
    scope: CoroutineScope,
    private val projectId: ProjectId,
    private val chatId: ChatId,
    private val onPush: (PagePush) -> Unit,
    private val isWebReady: () -> Boolean,
) : Disposable {

    private sealed interface Outgoing {
        class Post(val json: String) : Outgoing

        data object Ready : Outgoing
    }

    private val outgoing = Channel<Outgoing>(Channel.UNLIMITED)

    private val job = scope.launch {
        launch { send() }
        launch { receive() }
    }

    fun post(json: String) {
        outgoing.trySend(Outgoing.Post(json))
    }

    fun ready() {
        outgoing.trySend(Outgoing.Ready)
    }

    override fun dispose() {
        outgoing.close()
        job.cancel()
    }

    private suspend fun send() {
        for (item in outgoing) deliver(item)
    }

    private suspend fun deliver(item: Outgoing) {
        runCatching {
            val api = ChatApi.getInstance()
            when (item) {
                is Outgoing.Post -> api.post(projectId, chatId, item.json)
                Outgoing.Ready -> api.ready(projectId, chatId)
            }
        }.onFailure { cause ->
            if (cause !is Exception || cause is CancellationException) throw cause
            log.warn("Claude Code could not hand a chat page message to the host", cause)
        }
    }

    private suspend fun receive() {
        durable {
            val api = ChatApi.getInstance()
            val pushes = api.pushes(projectId, chatId)
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) { pushes.collect { onPush(it) } }
                if (isWebReady()) api.ready(projectId, chatId)
            }
        }
    }

    private companion object {
        private val log = logger<ChatLink>()
    }
}
