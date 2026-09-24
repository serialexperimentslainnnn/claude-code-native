package dev.lain.claudejb.frontend.window

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import dev.lain.claudejb.frontend.rpc.ChatClient
import dev.lain.claudejb.rpc.GearItem
import java.awt.Component

internal class HostOptionsAction(private val client: ChatClient) :
    AnAction("Claude Code Options", "Session, Git and settings entries of the Claude Code host", AllIcons.General.GearPlain),
    DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val anchor = e.inputEvent?.component
        client.gear { items -> show(anchor, items) }
    }

    private fun show(anchor: Component?, items: List<GearItem>) {
        if (client.project.isDisposed) return
        val group = GearMenuActions.group(items.ifEmpty { listOf(UNREACHABLE) }, client::runGear)
        val context = anchor?.let { DataManager.getInstance().getDataContext(it) }
            ?: SimpleDataContext.getProjectContext(client.project)
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
        val shown = anchor?.takeIf { it.isShowing }
        if (shown != null) popup.showUnderneathOf(shown) else popup.showCenteredInCurrentWindow(client.project)
    }

    private companion object {
        val UNREACHABLE = GearItem(emptyList(), "The Claude Code host did not answer", enabled = false)
    }
}
