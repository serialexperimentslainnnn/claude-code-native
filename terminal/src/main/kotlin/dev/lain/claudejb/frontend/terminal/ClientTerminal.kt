package dev.lain.claudejb.frontend.terminal

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import dev.lain.claudejb.frontend.window.TerminalOpener
import dev.lain.claudejb.rpc.TerminalLaunch

internal class ClientTerminal(private val project: Project) : TerminalOpener {

    override fun open(launch: TerminalLaunch) {
        runCatching {
            val tab = TerminalToolWindowTabsManager.getInstance(project)
                .createTabBuilder()
                .workingDirectory(launch.workingDirectory)
                .tabName(launch.tabName)
                .requestFocus(true)
                .deferSessionStartUntilUiShown(false)
                .createTab()
            tab.view.createSendTextBuilder().shouldExecute().send(launch.command)
        }.onFailure { log.warn("Failed to open IDE terminal for: ${launch.tabName}", it) }
    }

    private companion object {
        private val log = logger<ClientTerminal>()
    }
}
