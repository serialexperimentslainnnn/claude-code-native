package dev.lain.claudejb.view.feed

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import dev.lain.claudejb.controller.session.history.PluginAgentIndex
import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.model.session.agents.AgentStatus
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.view.payload.chat.JcefTabsData
import dev.lain.claudejb.view.window.ChatPresenter

internal class ChatAgentTabs(private val presenter: ChatPresenter) {

    private val project get() = presenter.project
    private val session get() = presenter.session

    private val hiddenAgents = HashSet<String>()

    init {
        session.sessionId?.let { id ->
            val index = PluginAgentIndex.getInstance(project)
            hiddenAgents += index.admittedAgents(id) - index.openAgents(id).toSet()
        }
    }

    fun render() {
        val windowMinutes = ClaudeSettings.getInstance(project).workloadWindowMinutes
        presenter.exec(
            "tabs",
            JcefTabsData.tabsJson(
                session,
                presenter.registry.chatList(),
                hiddenAgents,
                windowMinutes,
                System.currentTimeMillis(),
            ),
        )
    }

    fun onAgentsScanned(freshlyAdmitted: List<String>) {
        render()
        val fresh = freshlyAdmitted
            .filterNot { it in hiddenAgents }
            .filter { session.runningAgents.nodes[it]?.status == AgentStatus.RUNNING }
        if (fresh.isEmpty()) return
        notifyAgentsSpawned(fresh)
    }

    private fun notifyAgentsSpawned(fresh: List<String>) {
        if (presenter.registry.onScreen(presenter)) return
        val names = fresh.mapNotNull { session.runningAgents.nodes[it]?.meta?.label() }
        val text = when {
            names.size == 1 -> "Agent started in \"${session.title}\": ${names.first()}"
            names.isNotEmpty() -> "${names.size} agents started in \"${session.title}\""
            else -> return
        }
        NotificationGroupManager.getInstance().getNotificationGroup("Claude Code")
            .createNotification("Claude Code", text, NotificationType.INFORMATION)
            .addAction(
                NotificationAction.createSimpleExpiring("Open") {
                    presenter.registry.reveal(presenter)
                    fresh.firstOrNull()?.let { revealAgent(it) }
                },
            )
            .notify(project)
    }

    fun revealElsewhere(chatId: String, reveal: (ChatPresenter) -> Unit) {
        val target = chatId.takeIf { it.isNotBlank() }?.let { presenter.registry.presenter(ChatId(it)) }
        if (target == null || target === presenter) {
            reveal(presenter)
            return
        }
        presenter.registry.select(target.id)
        reveal(target)
    }

    fun revealFromHost(m: Msg.RevealAgent) {
        resolveAgentId(m)?.let { revealAgent(it) } ?: presenter.transcript.showTranscript(null)
    }

    private fun revealAgent(agentId: String) {
        if (hiddenAgents.remove(agentId)) {
            session.sessionId?.let { PluginAgentIndex.getInstance(project).setTabOpen(it, agentId, true) }
            render()
        }
        presenter.exec("revealAgentTab", JcefBridge.jsString(agentId))
        presenter.transcript.showTranscript(agentId)
    }

    fun closeAgent(agentId: String) {
        hiddenAgents += agentId
        session.sessionId?.let { PluginAgentIndex.getInstance(project).setTabOpen(it, agentId, false) }
        presenter.transcript.showTranscript(null)
        render()
    }

    private fun resolveAgentId(m: Msg.RevealAgent): String? {
        m.agentId.takeIf { it.isNotBlank() }?.let { return it }
        val tool = m.toolUseId.takeIf { it.isNotBlank() } ?: return null
        return session.runningAgents.nodes.values.firstOrNull { it.meta.toolUseId == tool }?.agentId
    }
}
