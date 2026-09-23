package dev.lain.claudejb.view.guard

import com.intellij.openapi.application.ApplicationManager
import dev.lain.claudejb.controller.commands.GuardPromptedActions
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.SecretStore
import dev.lain.claudejb.model.settings.SettingsScope
import dev.lain.claudejb.model.settings.guard.GuardAlert
import dev.lain.claudejb.model.settings.guard.GuardAlertLog
import dev.lain.claudejb.util.edt
import dev.lain.claudejb.util.logger
import dev.lain.claudejb.view.window.ChatPresenter

internal class GuardFeed(private val presenter: ChatPresenter) {

    fun push() {
        val scope = scope()
        val sessionId = presenter.session.sessionId
        val recorded = presenter.session.guard.guardLog.recorded
        val dropped = presenter.session.guard.guardLog.dropped
        offEdt {
            val json = JcefGuardData.guardJson(
                alerts = read(scope, sessionId),
                recorded = recorded,
                dropped = dropped,
                recording = !SecretStore.inert(),
                max = GuardAlertLog.MAX_ENTRIES,
            )
            edt(presenter.project) { presenter.exec("guard", json) }
        }
    }

    fun explain(id: String) {
        val scope = scope()
        val sessionId = presenter.session.sessionId
        offEdt {
            val alert = read(scope, sessionId).firstOrNull { JcefGuardData.idOf(it) == id }
            val prompt = alert?.let(GuardPromptedActions::explainBlockPrompt)
            edt(presenter.project) {
                if (prompt == null) {
                    presenter.session.systemNotice(GuardPromptedActions.ENTRY_GONE)
                } else {
                    presenter.session.sendSideQuestion(prompt)
                }
            }
        }
    }

    private fun scope(): SettingsScope = ClaudeSettings.getInstance(presenter.project).scope

    private fun read(scope: SettingsScope, sessionId: String?): List<GuardAlert> {
        if (sessionId.isNullOrBlank()) return emptyList()
        return runCatching { GuardAlertLog.forSession(scope, sessionId) }
            .onFailure { logger.warn("Claude Code could not read the guard alert log", it) }
            .getOrDefault(emptyList())
    }

    private fun offEdt(block: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching(block).onFailure { logger.warn("Claude Code could not answer the guard view", it) }
        }
    }

    private companion object {
        private val logger = logger<GuardFeed>()
    }
}
