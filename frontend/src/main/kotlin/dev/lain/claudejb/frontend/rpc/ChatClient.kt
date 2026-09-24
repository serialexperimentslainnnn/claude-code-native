package dev.lain.claudejb.frontend.rpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.project.projectId
import dev.lain.claudejb.rpc.ChatApi
import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.GearItem
import dev.lain.claudejb.rpc.HostWindowApi
import dev.lain.claudejb.rpc.PagePush
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
class ChatClient(val project: Project, private val scope: CoroutineScope) {

    private var listener: ChatListener? = null

    private var following: Job? = null

    fun connect(parent: Disposable, listener: ChatListener) {
        this.listener = listener
        following = scope.launch { if (reachable()) follow(listener) else onEdt { listener.unavailable() } }
        Disposer.register(parent) {
            following?.cancel()
            this.listener = null
        }
    }

    fun resync() {
        val current = listener ?: return
        following?.cancel()
        following = scope.launch { follow(current) }
    }

    fun watchWindow() {
        project.messageBus.connect(scope).subscribe(ToolWindowManagerListener.TOPIC, ChatWindowVisibility(::reportVisible))
        scope.launch {
            durable { HostWindowApi.getInstance().reveals(project.projectId()).collect { onEdt(::revealWindow) } }
        }
    }

    fun gear(onItems: (List<GearItem>) -> Unit) {
        scope.launch {
            val items = fetchGear()
            onEdt { onItems(items) }
        }
    }

    fun runGear(path: List<Int>) = tell("Claude Code could not run the host's menu entry") {
        HostWindowApi.getInstance().runGear(project.projectId(), path)
    }

    fun link(chatId: ChatId, parent: Disposable, onPush: (PagePush) -> Unit): ChatLink =
        ChatLink(scope, project.projectId(), chatId, onPush).also { Disposer.register(parent, it) }

    private fun reportVisible(visible: Boolean) = tell("Claude Code could not tell the host whether its window is visible") {
        HostWindowApi.getInstance().windowVisible(project.projectId(), visible)
    }

    private fun revealWindow() {
        ToolWindowManager.getInstance(project).getToolWindow(ChatWindowVisibility.TOOL_WINDOW_ID)?.activate(null)
    }

    private fun tell(failure: String, call: suspend () -> Unit) {
        scope.launch {
            runCatching { call() }.onFailure { cause ->
                if (cause !is Exception || cause is CancellationException) throw cause
                log.warn(failure, cause)
            }
        }
    }

    private suspend fun reachable(): Boolean =
        runCatching { withTimeout(CONNECT_TIMEOUT) { ChatApi.getInstance().chats(project.projectId()) } }
            .map { true }
            .getOrElse { cause ->
                when (cause) {
                    is TimeoutCancellationException -> log.warn("Claude Code chat host did not answer within $CONNECT_TIMEOUT")
                    !is Exception, is CancellationException -> throw cause
                    else -> log.warn("Claude Code chat host is not reachable from this client", cause)
                }
                false
            }

    private suspend fun fetchGear(): List<GearItem> =
        runCatching { withTimeout(GEAR_TIMEOUT) { HostWindowApi.getInstance().gear(project.projectId()) } }
            .getOrElse { cause ->
                when (cause) {
                    is TimeoutCancellationException -> log.warn("Claude Code chat host did not list its menu within $GEAR_TIMEOUT")
                    !is Exception, is CancellationException -> throw cause
                    else -> log.warn("Claude Code could not read the host's menu", cause)
                }
                emptyList()
            }

    private suspend fun follow(listener: ChatListener) {
        val projectId = project.projectId()
        durable {
            ChatApi.getInstance().events(projectId).collect { event -> onEdt { deliver(listener, event) } }
        }
    }

    private fun deliver(listener: ChatListener, event: ChatEvent) {
        runCatching { listener.event(event) }.onFailure { cause ->
            if (cause !is Exception || cause is CancellationException) throw cause
            log.error("Claude Code could not apply a chat event to this window: $event", cause)
        }
    }

    private suspend fun onEdt(block: () -> Unit) =
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) { block() }

    private companion object {
        private val log = logger<ChatClient>()

        private val CONNECT_TIMEOUT = 30.seconds

        private val GEAR_TIMEOUT = 10.seconds
    }
}
