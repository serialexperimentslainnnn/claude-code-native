package dev.lain.claudejb.view.window

import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.process.TerminalLauncher
import dev.lain.claudejb.rpc.FrontendChannel
import dev.lain.claudejb.rpc.TerminalLaunch
import kotlinx.serialization.json.Json

internal class ChatTerminalHost(private val project: Project) : TerminalLauncher.Host {

    override fun open(workingDirectory: String?, tabName: String, command: String): Boolean {
        val chat = ChatRegistry.getInstance(project).selected() ?: return false
        if (!chat.pushes.hasSinks()) return false
        val launch = TerminalLaunch(workingDirectory, tabName, command)
        chat.exec(FrontendChannel.TERMINAL, Json.encodeToString(TerminalLaunch.serializer(), launch))
        return true
    }
}
