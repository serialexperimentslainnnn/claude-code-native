package dev.lain.claudejb.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow

@Rpc
@Suppress("SuspendFunWithFlowReturnType")
interface ChatApi : RemoteApi<Unit> {
    suspend fun chats(projectId: ProjectId): List<ChatRef>

    suspend fun events(projectId: ProjectId): Flow<ChatEvent>

    suspend fun newChat(projectId: ProjectId): ChatRef

    suspend fun select(projectId: ProjectId, chatId: ChatId)

    suspend fun close(projectId: ProjectId, chatId: ChatId)

    suspend fun post(projectId: ProjectId, chatId: ChatId, json: String)

    suspend fun pushes(projectId: ProjectId, chatId: ChatId): Flow<PagePush>

    companion object {
        suspend fun getInstance(): ChatApi = RemoteApiProviderService.resolve(remoteApiDescriptor<ChatApi>())
    }
}
