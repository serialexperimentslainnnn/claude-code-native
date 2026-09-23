package dev.lain.claudejb.controller.commands.git

import com.intellij.openapi.project.Project
import dev.lain.claudejb.util.logger
import dev.lain.claudejb.view.git.JcefGitData
import dev.lain.claudejb.view.window.ChatRegistry

internal object IdeActionInvoker {

    private val LOG = logger<IdeActionInvoker>()

    fun invoke(project: Project, actionId: String, gitActionId: String): JcefGitData.ActionState {
        val presenter = ChatRegistry.getInstance(project).selected() ?: run {
            LOG.warn("No chat is open to run '$actionId' for the Git view's '$gitActionId' button")
            return JcefGitData.ActionState.FAILED
        }
        presenter.runIdeAction(actionId)
        return JcefGitData.ActionState.COMPLETED
    }
}
