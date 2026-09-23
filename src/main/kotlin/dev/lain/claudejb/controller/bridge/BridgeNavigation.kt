package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.view.window.ChatPresenter

internal class BridgeNavigation(private val presenter: ChatPresenter) {

    fun handle(m: Msg.Navigation) {
        when (m) {
            is Msg.RevealAgent -> presenter.agentTabs.revealElsewhere(m.chatId) { it.agentTabs.revealFromHost(m) }

            is Msg.RevealBackgroundTask ->
                presenter.agentTabs.revealElsewhere(m.chatId) { it.transcript.showBackgroundTask(m.taskId) }

            Msg.ShowChatTranscript -> presenter.transcript.showTranscript(null)

            is Msg.SelectChat -> presenter.registry.select(ChatId(m.chatId))

            is Msg.CloseChat -> presenter.registry.close(ChatId(m.chatId))

            is Msg.SelectAgent -> presenter.transcript.showTranscript(m.agentId.ifBlank { null })

            is Msg.CloseAgent -> presenter.agentTabs.closeAgent(m.agentId)
        }
    }
}
