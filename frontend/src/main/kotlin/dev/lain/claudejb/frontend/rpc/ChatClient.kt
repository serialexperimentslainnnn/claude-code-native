package dev.lain.claudejb.frontend.rpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import dev.lain.claudejb.rpc.ChatApi
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.PagePush
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Service(Service.Level.PROJECT)
class ChatClient(val project: Project, private val scope: CoroutineScope) {

    fun connect(parent: Disposable, listener: ChatListener) {
        val job = scope.launch {
            if (reachable()) follow(listener) else onEdt { listener.unavailable() }
        }
        Disposer.register(parent) { job.cancel() }
    }

    fun select(chatId: ChatId) {
        scope.launch {
            runCatching { ChatApi.getInstance().select(project.projectId(), chatId) }.onFailure { cause ->
                if (cause !is Exception || cause is CancellationException) throw cause
                log.warn("Claude Code could not tell the host which chat is on screen", cause)
            }
        }
    }

    fun link(
        chatId: ChatId,
        parent: Disposable,
        onPush: (PagePush) -> Unit,
        isWebReady: () -> Boolean,
    ): ChatLink = ChatLink(scope, project.projectId(), chatId, onPush, isWebReady).also { Disposer.register(parent, it) }

    private suspend fun reachable(): Boolean =
        runCatching { withTimeout(CONNECT_TIMEOUT_MS) { ChatApi.getInstance().chats(project.projectId()) } }
            .map { true }
            .getOrElse { cause ->
                when (cause) {
                    is TimeoutCancellationException -> log.warn("Claude Code chat host did not answer within ${CONNECT_TIMEOUT_MS}ms")
                    !is Exception, is CancellationException -> throw cause
                    else -> log.warn("Claude Code chat host is not reachable from this client", cause)
                }
                false
            }

    private suspend fun follow(listener: ChatListener) {
        val projectId = project.projectId()
        durable {
            val api = ChatApi.getInstance()
            val events = api.events(projectId)
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    events.collect { event -> onEdt { listener.event(event) } }
                }
                val chats = api.chats(projectId).ifEmpty { listOf(api.newChat(projectId)) }
                onEdt { listener.sync(chats) }
            }
        }
    }

    private suspend fun onEdt(block: () -> Unit) =
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) { block() }

    private companion object {
        private val log = logger<ChatClient>()

        private const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
