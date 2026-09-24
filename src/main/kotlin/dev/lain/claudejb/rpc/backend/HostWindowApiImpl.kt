package dev.lain.claudejb.rpc.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.project.ProjectId
import dev.lain.claudejb.rpc.GearItem
import dev.lain.claudejb.rpc.HostWindowApi
import dev.lain.claudejb.rpc.backend.RpcProjects.onEdt
import dev.lain.claudejb.rpc.backend.RpcProjects.project
import dev.lain.claudejb.rpc.backend.RpcProjects.registry
import dev.lain.claudejb.view.window.GearMenu
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.concurrent.atomic.AtomicInteger

internal class HostWindowApiImpl : HostWindowApi {

    override suspend fun reveals(projectId: ProjectId): Flow<Int> {
        val registry = registry(projectId) ?: return emptyFlow()
        return callbackFlow {
            val count = AtomicInteger()
            val stop = registry.window.onReveal { trySend(count.incrementAndGet()) }
            awaitClose(stop)
        }.buffer(Channel.CONFLATED)
    }

    override suspend fun windowVisible(projectId: ProjectId, visible: Boolean) {
        registry(projectId)?.window?.visible = visible
    }

    override suspend fun gear(projectId: ProjectId): List<GearItem> {
        val project = project(projectId) ?: return emptyList()
        return onEdt { GearMenu.items(project) }
    }

    override suspend fun runGear(projectId: ProjectId, path: List<Int>) {
        val project = project(projectId) ?: return
        ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) GearMenu.run(project, path) }
    }
}
