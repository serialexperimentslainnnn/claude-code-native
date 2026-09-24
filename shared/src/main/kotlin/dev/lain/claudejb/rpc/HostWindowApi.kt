package dev.lain.claudejb.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow

@Rpc
@Suppress("SuspendFunWithFlowReturnType")
interface HostWindowApi : RemoteApi<Unit> {
    suspend fun reveals(projectId: ProjectId): Flow<Int>

    suspend fun windowVisible(projectId: ProjectId, visible: Boolean)

    suspend fun gear(projectId: ProjectId): List<GearItem>

    suspend fun runGear(projectId: ProjectId, path: List<Int>)

    companion object {
        suspend fun getInstance(): HostWindowApi = RemoteApiProviderService.resolve(remoteApiDescriptor<HostWindowApi>())
    }
}
