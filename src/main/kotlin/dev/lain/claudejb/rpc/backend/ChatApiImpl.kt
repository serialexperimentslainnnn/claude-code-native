package dev.lain.claudejb.rpc.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.project.ProjectId
import dev.lain.claudejb.rpc.ChatApi
import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.rpc.backend.RpcProjects.onEdt
import dev.lain.claudejb.rpc.backend.RpcProjects.presenter
import dev.lain.claudejb.rpc.backend.RpcProjects.registry
import dev.lain.claudejb.view.window.PushSink
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
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
        onEdt { registry.select(chatId, focus = false) }
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
}
