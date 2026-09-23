package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.controller.commands.git.GitActionCatalog
import dev.lain.claudejb.controller.commands.git.GitIntegration
import dev.lain.claudejb.controller.mcp.IdeMcpService
import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.WorkloadWindow
import dev.lain.claudejb.util.thisLogger
import dev.lain.claudejb.view.window.ChatPresenter
import dev.lain.claudejb.view.window.ChatRegistry
import dev.lain.claudejb.view.window.ChatSnapshots.Kind

internal class BridgeSessionControl(private val presenter: ChatPresenter) {

    private val log = thisLogger()

    private val vuln = BridgeVuln(presenter)

    private val navigation = BridgeNavigation(presenter)

    private val session get() = presenter.session

    fun handle(m: Msg.SessionControl) {
        when (m) {
            is Msg.Vuln -> vuln.handle(m)

            is Msg.Navigation -> navigation.handle(m)

            is Msg.Onboarding -> presenter.onboarding.handle(m)

            Msg.McpRefresh -> presenter.feed.requestMcp()

            is Msg.McpReconnect -> {
                IdeMcpService.getInstance(session.project).admitReconnect(m.name)
                session.queries.reconnectMcp(m.name)
                presenter.feed.requestMcp()
            }

            is Msg.McpToggle -> {
                session.queries.toggleMcp(m.name, m.enabled)
                presenter.feed.requestMcp()
            }

            is Msg.StopTask -> session.queries.stopTask(m.taskId)

            is Msg.SetWorkloadWindow -> workloadWindow(m.minutes)

            is Msg.GitAction -> gitAction(m)

            Msg.NewChat -> presenter.registry.newChat()

            Msg.CloseThisChat -> presenter.registry.close(presenter.id)

            Msg.OpenGitView -> presenter.showGitView()
        }
    }

    private fun gitAction(m: Msg.GitAction) {
        GitIntegration.getInstance(presenter.project)
            .perform(m.id, m.hash, { presenter.gitChat.session() }) { presenter.registry.git.request() }
        if (GitActionCatalog.byId(m.id)?.kind == GitActionCatalog.Kind.PROMPT) presenter.gitChat.show()
    }

    private fun workloadWindow(minutes: Int) {
        if (minutes !in WorkloadWindow.WINDOW_MINUTES) {
            log.warn("Workloads view asked for a window this build does not offer: $minutes")
            return
        }
        ClaudeSettings.getInstance(presenter.project).update { it.workloadWindowMinutes = minutes }
        ChatRegistry.repaintEverywhere(Kind.SESSION)
    }
}
