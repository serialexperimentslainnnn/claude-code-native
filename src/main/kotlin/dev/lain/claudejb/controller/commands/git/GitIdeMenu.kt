package dev.lain.claudejb.controller.commands.git

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.commands.GearChildren
import dev.lain.claudejb.controller.git.GitAvailability

internal object GitIdeMenu {

    fun gearEntry(): AnAction = IdeGitGroup()

    private class IdeGitGroup :
        ActionGroup("Git Operations", "The IDE's own Git actions", null),
        GearChildren {

        init {
            isPopup = true
        }

        override fun getChildren(e: AnActionEvent?): Array<AnAction> = entries().toTypedArray()

        override fun children(project: Project): List<AnAction> = entries()

        private fun entries(): List<AnAction> {
            if (!GitAvailability.isGitPluginEnabled()) return emptyList()
            val actions = ActionManager.getInstance()
            val blocks = mutableListOf<MutableList<AnAction>>()
            for (entry in GitActionCatalog.ideActions()) {
                if (entry.startsBlock || blocks.isEmpty()) blocks.add(mutableListOf())
                val action = entry.ideActionId?.let { actions.getAction(it) } ?: continue
                blocks.last().add(action)
            }
            return buildList {
                for (block in blocks.filter { it.isNotEmpty() }) {
                    if (isNotEmpty()) add(Separator.getInstance())
                    addAll(block)
                }
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isVisible = GitAvailability.isGitPluginEnabled()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
}
