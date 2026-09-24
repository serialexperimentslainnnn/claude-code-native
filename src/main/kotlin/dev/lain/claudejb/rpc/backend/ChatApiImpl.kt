package dev.lain.claudejb.rpc.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import dev.lain.claudejb.rpc.ChatApi
import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import dev.lain.claudejb.rpc.GearItem
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.view.window.ChatPresenter
import dev.lain.claudejb.view.window.ChatRegistry
import dev.lain.claudejb.view.window.GearMenu
import dev.lain.claudejb.view.window.PushSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

internal class ChatApiImpl : ChatApi {

    override suspend fun chats(projectId: ProjectId): List<ChatRef> {
        val registry = registry(projectId) ?: return emptyList()
        return onEdt { registry.chats() }
    }

    override suspend fun events(projectId: ProjectId): Flow<ChatEvent> {
        val registry = registry(projectId) ?: return emptyFlow()
        return callbackFlow {
            val unsubscribe = onEdt { registry.subscribe { trySend(it) } }
            awaitClose(unsubscribe)
        }.buffer(Channel.UNLIMITED)
    }

    override suspend fun newChat(projectId: ProjectId): ChatRef {
        val registry = registry(projectId) ?: error("No open project for $projectId")
        return onEdt { registry.newChat().ref() }
    }

    override suspend fun select(projectId: ProjectId, chatId: ChatId) {
        val registry = registry(projectId) ?: return
        onEdt { registry.select(chatId) }
    }

    override suspend fun close(projectId: ProjectId, chatId: ChatId) {
        val registry = registry(projectId) ?: return
        onEdt { registry.close(chatId) }
    }

    override suspend fun post(projectId: ProjectId, chatId: ChatId, json: String) {
        val presenter = presenter(projectId, chatId) ?: return
        ApplicationManager.getApplication().invokeLater { presenter.router.dispatch(json) }
    }

    override suspend fun pushes(projectId: ProjectId, chatId: ChatId): Flow<PagePush> {
        val presenter = presenter(projectId, chatId) ?: return emptyFlow()
        return callbackFlow {
            val detach = presenter.pushes.attach(
                object : PushSink {
                    override fun push(push: PagePush) {
                        trySend(push)
                    }

                    override fun close() {
                        channel.close()
                    }
                },
            )
            awaitClose(detach)
        }.buffer(Channel.UNLIMITED)
    }

    override suspend fun ready(projectId: ProjectId, chatId: ChatId) {
        val presenter = presenter(projectId, chatId) ?: return
        presenter.replay()
    }

    override suspend fun gear(projectId: ProjectId): List<GearItem> {
        val project = project(projectId) ?: return emptyList()
        return onEdt { GearMenu.items(project) }
    }

    override suspend fun runGear(projectId: ProjectId, path: List<Int>) {
        val project = project(projectId) ?: return
        ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) GearMenu.run(project, path) }
    }

    private fun project(projectId: ProjectId): Project? = projectId.findProjectOrNull()?.takeUnless { it.isDisposed }

    private fun registry(projectId: ProjectId): ChatRegistry? = project(projectId)?.let(ChatRegistry::getInstance)

    private fun presenter(projectId: ProjectId, chatId: ChatId): ChatPresenter? = registry(projectId)?.presenter(chatId)

    private suspend fun <T> onEdt(block: () -> T): T {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) return block()
        val result = CompletableDeferred<T>()
        app.invokeLater({ result.completeWith(runCatching(block)) }, ModalityState.any())
        return result.await()
    }
}
