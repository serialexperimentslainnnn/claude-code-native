package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.view.window.ChatPresenter

internal class ChatBridgeRouter(presenter: ChatPresenter) {

    private val prompting = BridgePrompting(presenter)
    private val settings = BridgeSettings(presenter)
    private val cards = BridgeCards(presenter)
    private val diffs = BridgeDiffs(presenter)
    private val attachments = BridgeAttachments(presenter)
    private val controls = BridgeSessionControl(presenter)
    private val lifecycle = BridgeLifecycle(presenter)
    private val log = BridgeLog(presenter)

    fun dispatch(json: String) {
        when (val m = JcefBridge.parse(json)) {
            is Msg.Prompting -> prompting.handle(m)
            is Msg.Settings -> settings.handle(m)
            is Msg.RequestCard -> cards.handle(m)
            is Msg.Diffs -> diffs.handle(m)
            is Msg.Attachments -> attachments.handle(m)
            is Msg.SessionControl -> controls.handle(m)
            is Msg.Lifecycle -> lifecycle.handle(m)
            is Msg.Log -> log.handle(m)
        }
    }
}
