package dev.lain.claudejb.rpc.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.view.window.ChatPresenter
import dev.lain.claudejb.view.window.ChatRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith

internal object RpcProjects {

    fun project(projectId: ProjectId): Project? = projectId.findProjectOrNull()?.takeUnless { it.isDisposed }

    fun registry(projectId: ProjectId): ChatRegistry? = project(projectId)?.let(ChatRegistry::getInstance)

    fun presenter(projectId: ProjectId, chatId: ChatId): ChatPresenter? = registry(projectId)?.presenter(chatId)

    suspend fun <T> onEdt(block: () -> T): T {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) return block()
        val result = CompletableDeferred<T>()
        app.invokeLater({ result.completeWith(runCatching(block)) }, ModalityState.any())
        return result.await()
    }
}
