package dev.lain.claudejb.view.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import dev.lain.claudejb.controller.commands.git.GitIntegration
import dev.lain.claudejb.controller.git.GitHistoryService

internal class ChatGitRefresh(
    private val project: Project,
    parent: Disposable,
    private val onRefreshed: () -> Unit,
) {

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parent)

    init {
        project.service<GitHistoryService>().onRepositoryChanged(parent) { request() }
    }

    fun request() {
        if (alarm.isDisposed) return
        alarm.cancelAllRequests()
        alarm.addRequest({ if (!project.isDisposed) GitIntegration.getInstance(project).refresh(onRefreshed) }, DEBOUNCE_MS)
    }

    private companion object {
        const val DEBOUNCE_MS = 500
    }
}
