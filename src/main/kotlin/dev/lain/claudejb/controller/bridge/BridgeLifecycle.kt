package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.util.thisLogger
import dev.lain.claudejb.view.window.ChatPresenter

internal class BridgeLifecycle(private val presenter: ChatPresenter) {

    private val log = thisLogger()

    fun handle(m: Msg.Lifecycle) {
        when (m) {
            Msg.Ready -> presenter.refreshPage()

            is Msg.Diagnostics ->
                if (m.report.startsWith("uncaught ")) {
                    log.warn("Claude Code chat page: ${m.report}")
                } else {
                    log.info("JCEF diagnostics: ${m.report}")
                }

            is Msg.Unknown -> log.warn("the chat page sent a message this build does not parse: ${m.type}")
        }
    }
}
