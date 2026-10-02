package dev.lain.claudejb.frontend.window

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.logger
import javax.swing.JComponent

internal object IdeActionRunner {

    private val log = logger<IdeActionRunner>()

    fun run(component: JComponent, actionId: String) {
        val target = ActionManager.getInstance().getAction(actionId) ?: run {
            log.warn("This IDE has no action '$actionId'; the Git view's button does nothing")
            return
        }
        val event = AnActionEvent.createEvent(
            target,
            DataManager.getInstance().getDataContext(component),
            null,
            ActionPlaces.TOOLWINDOW_CONTENT,
            ActionUiKind.TOOLBAR,
            null,
        )
        ActionUtil.updateAction(target, event)
        if (!event.presentation.isEnabled || !event.presentation.isVisible) {
            log.warn("The IDE refused '$actionId' in this context (enabled=${event.presentation.isEnabled})")
            return
        }
        ActionUtil.performAction(target, event)
    }
}
