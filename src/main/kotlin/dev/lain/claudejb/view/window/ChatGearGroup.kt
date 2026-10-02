package dev.lain.claudejb.view.window

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.commands.GearChildren
import dev.lain.claudejb.controller.commands.SessionDiffAction
import dev.lain.claudejb.controller.commands.git.GitContextActions
import dev.lain.claudejb.controller.commands.git.GitIdeMenu
import dev.lain.claudejb.controller.commands.git.GitPromptedActions
import dev.lain.claudejb.controller.session.ChatSessionManager
import dev.lain.claudejb.view.settings.ClaudeSettingsConfigurable

internal class ChatGearGroup : GearChildren {

    override fun children(project: Project): List<AnAction> =
        if (project.isDisposed) emptyList() else entries(project, ChatRegistry.getInstance(project))

    private fun entries(project: Project, registry: ChatRegistry): List<AnAction> = buildList {
        val selected = { registry.selected() }
        add(simple("Session Info (Context · Cost · Account · MCP)…") { selected()?.openDashboard() })
        add(simple("Dependency Vulnerabilities…") { selected()?.security?.showVulnView() })
        add(simple("Agents") { selected()?.let { InfoDialogs.showAgents(project, it.session) } })
        add(simple("Security Guard Log (Blocked · Allowed · Whitelisted · Disabled)") { selected()?.security?.openGuardView() })
        add(SessionDiffAction(project))
        add(simple("Binary Version…") { selected()?.let { InfoDialogs.showBinaryVersion(project, it.session) } })
        add(simple("Effective Settings…") { selected()?.let { InfoDialogs.showEffectiveSettings(project, it.session) } })
        add(Separator.getInstance())
        add(simple("Rename Session…") { registry.commands.renameActiveSession() })
        add(simple("Fork Session") { registry.commands.forkActiveSession() })
        add(simple("Open Previous Session…") { registry.commands.openPreviousSession() })
        add(simple("Add Current File as @-context") { selected()?.mentionCurrentFile() })
        add(Separator.getInstance())
        addAll(GitContextActions.gearEntries(project))
        addAll(
            GitPromptedActions.gearEntries(project) {
                selected()?.gitChat?.session() ?: ChatSessionManager.getInstance(project).gitChatOrCreate()
            },
        )
        add(GitIdeMenu.gearEntry())
        add(Separator.getInstance())
        add(
            simple("Settings…") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, ClaudeSettingsConfigurable::class.java)
            },
        )
    }

    private fun simple(text: String, action: () -> Unit): AnAction = object : AnAction(text) {
        override fun actionPerformed(e: AnActionEvent) = action()
    }
}
