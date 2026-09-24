package dev.lain.claudejb.view.window

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.commands.GearChildren
import dev.lain.claudejb.rpc.GearItem
import dev.lain.claudejb.util.logger

internal object GearMenu {

    private val log = logger<GearMenu>()

    fun items(project: Project): List<GearItem> = describe(project, ChatGearGroup(), emptyList())

    private fun describe(project: Project, group: GearChildren, prefix: List<Int>): List<GearItem> =
        group.children(project).mapIndexedNotNull { index, action -> item(project, action, prefix + index) }

    fun run(project: Project, path: List<Int>) {
        val action = find(project, path) ?: return log.warn("Claude Code gear: no entry at $path any more")
        val event = event(project, action)
        ActionUtil.updateAction(action, event)
        if (!event.presentation.isEnabledAndVisible) {
            return log.warn("Claude Code gear: '${event.presentation.text}' is not available now")
        }
        ActionUtil.performAction(action, event)
    }

    private fun item(project: Project, action: AnAction, path: List<Int>): GearItem? {
        if (action is Separator) return GearItem(path, "", enabled = false, separator = true)
        val event = event(project, action)
        ActionUtil.updateAction(action, event)
        val presentation = event.presentation
        if (!presentation.isVisible) return null
        val children = (action as? GearChildren)?.let { describe(project, it, path) }.orEmpty()
        return GearItem(path, presentation.text.orEmpty(), presentation.isEnabled, children = children)
    }

    private fun find(project: Project, path: List<Int>): AnAction? {
        if (path.isEmpty()) return null
        var group: GearChildren = ChatGearGroup()
        var action: AnAction? = null
        for (index in path) {
            action = group.children(project).getOrNull(index) ?: return null
            group = action as? GearChildren ?: continue
        }
        return action
    }

    private fun event(project: Project, action: AnAction): AnActionEvent =
        AnActionEvent.createEvent(
            action,
            SimpleDataContext.getProjectContext(project),
            null,
            ActionPlaces.TOOLWINDOW_POPUP,
            ActionUiKind.POPUP,
            null,
        )
}
