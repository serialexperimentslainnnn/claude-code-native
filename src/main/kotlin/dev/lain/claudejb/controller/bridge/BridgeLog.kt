package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.view.window.ChatPresenter

internal class BridgeLog(private val presenter: ChatPresenter) {

    fun handle(m: Msg.Log) {
        when (m) {
            is Msg.LogLines -> presenter.logFeed.push(m.since)
            is Msg.LogDebug -> presenter.logFeed.setDebug(m.on)
            is Msg.LogCopy -> presenter.logFeed.copy(m.level)
        }
    }
}
