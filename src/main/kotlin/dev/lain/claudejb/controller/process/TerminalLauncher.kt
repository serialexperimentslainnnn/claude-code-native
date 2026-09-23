package dev.lain.claudejb.controller.process

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import dev.lain.claudejb.util.InstalledPlugins

object TerminalLauncher {

    fun interface Host {
        fun open(workingDirectory: String?, tabName: String, command: String): Boolean
    }

    private const val TERMINAL_PLUGIN_ID = "org.jetbrains.plugins.terminal"

    fun isAvailable(): Boolean = InstalledPlugins.isEnabled(TERMINAL_PLUGIN_ID)

    fun commandLine(
        binaryPath: String,
        args: List<String> = emptyList(),
        isWindows: Boolean = SystemInfo.isWindows,
    ): String {
        val quoted = (listOf("\"$binaryPath\"") + args).joinToString(" ")
        return if (isWindows) "& $quoted" else quoted
    }

    fun loginCommand(
        binaryPath: String,
        args: List<String> = listOf("auth", "login"),
        isWindows: Boolean = SystemInfo.isWindows,
    ): String = commandLine(binaryPath, args, isWindows)

    fun openAndRunCommand(project: Project, argv: List<String>, tabName: String): Boolean {
        if (!isAvailable()) return false
        if (argv.isEmpty()) return false
        val host = project.serviceOrNull<Host>() ?: return false
        return host.open(project.basePath, tabName, commandLine(argv.first(), argv.drop(1)))
    }
}
