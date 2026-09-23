package dev.lain.claudejb.frontend.window

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.lain.claudejb.frontend.jcef.PageAssembly
import dev.lain.claudejb.frontend.rpc.ChatClient
import java.util.concurrent.Callable

class ClaudeToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        ApplicationManager.getApplication().executeOnPooledThread(Callable { PageAssembly.page })
        val client = project.service<ChatClient>()
        lateinit var tabs: ChatTabsPanel
        tabs = ChatTabsPanel(client::select) { id ->
            ChatView(id, client) { toolWindow.show { tabs.focus(id) } }
        }
        val content = ContentFactory.getInstance().createContent(tabs, "", false)
        content.isCloseable = false
        content.setPreferredFocusedComponent { tabs.focusTarget() }
        content.setDisposer(tabs)
        toolWindow.contentManager.addContent(content)
        (ActionManager.getInstance().getAction(GEAR_GROUP) as? ActionGroup)?.let(toolWindow::setAdditionalGearActions)
        client.connect(tabs, tabs)
    }

    private companion object {
        const val GEAR_GROUP = "ClaudeCode.ToolWindowGear"
    }
}
