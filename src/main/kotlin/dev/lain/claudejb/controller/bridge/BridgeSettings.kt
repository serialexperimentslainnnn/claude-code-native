package dev.lain.claudejb.controller.bridge

import com.intellij.openapi.options.ShowSettingsUtil
import dev.lain.claudejb.controller.session.ChatSessionManager
import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.LaunchDefaults
import dev.lain.claudejb.model.settings.Provider
import dev.lain.claudejb.util.thisLogger
import dev.lain.claudejb.view.payload.menu.JcefSettingsMenu
import dev.lain.claudejb.view.settings.ClaudeSettingsConfigurable
import dev.lain.claudejb.view.window.ChatPresenter
import dev.lain.claudejb.view.window.ChatRegistry
import dev.lain.claudejb.view.window.ChatSnapshots.Kind

internal class BridgeSettings(private val presenter: ChatPresenter) {

    private val log = thisLogger()

    private val guard = BridgeGuard(presenter)

    private val session get() = presenter.session

    private val repaintMenu = { ChatRegistry.repaintEverywhere(Kind.MENU) }

    fun handle(m: Msg.Settings) {
        when (m) {
            is Msg.ChangeModel -> session.settings.changeModel(m.value)

            is Msg.ChangeMode -> session.settings.changePermissionMode(m.wire)

            is Msg.ChangeEffort -> session.settings.changeEffort(m.value)

            is Msg.ChangeThinking ->
                session.settings.changeThinkingTokens(if (m.on) LaunchDefaults.THINKING_ON else null)

            is Msg.ChangeVibe -> ChatRegistry.changeVibe(m.on)

            is Msg.ChangeProvider -> session.settings.changeProvider(Provider.fromId(m.id))

            is Msg.SettingsToggle -> toggle(m)

            is Msg.Guard -> guard.handle(m)

            Msg.SettingsRefresh -> ClaudeSettings.getInstance(presenter.project).reload(repaintMenu)

            Msg.OpenSettings ->
                ShowSettingsUtil.getInstance().showSettingsDialog(presenter.project, ClaudeSettingsConfigurable::class.java)
        }
    }

    private fun toggle(m: Msg.SettingsToggle) {
        if (!write(m)) {
            log.warn("The chat's settings menu asked for a switch this build does not have: ${m.key}")
            return
        }
        repaintMenu()
    }

    private fun write(m: Msg.SettingsToggle): Boolean {
        val settings = ClaudeSettings.getInstance(presenter.project)
        JcefSettingsMenu.alwaysAllowTool(m.key)?.let { tool ->
            if (m.on) settings.alwaysAllow.remember(tool) else settings.alwaysAllow.forget(tool)
            return true
        }
        JcefSettingsMenu.sessionApproval(m.key)?.let { (rule, command) ->
            if (!m.on) session.guard.approvals.revoke(rule, command)
            return true
        }
        if (JcefSettingsMenu.isRemoteControl(m.key)) {
            session.remote.set(m.on, repaintMenu)
            return true
        }
        val scope = settings.scope.id
        val models = session.catalog.models.map { it.value }
        if (!JcefSettingsMenu.apply(scope, settings.state, m.key, m.on, models)) return false
        settings.update { JcefSettingsMenu.apply(scope, it, m.key, m.on, models) }
        ChatSessionManager.getInstance(presenter.project).adoptSettings()
        return true
    }
}
