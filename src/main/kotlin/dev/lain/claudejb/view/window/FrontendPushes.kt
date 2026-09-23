package dev.lain.claudejb.view.window

import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.rpc.FrontendChannel

internal class FrontendPushes(private val exec: (String, String) -> Unit) {

    fun pushVibe() = exec(FrontendChannel.VIBE, ChatRegistry.vibe.toString())

    fun focusInput() = exec(FrontendChannel.FOCUS, PushStream.NO_ARGS)

    fun copyToClient(text: String) = exec(FrontendChannel.COPY, JcefBridge.jsString(text))

    fun runIdeAction(actionId: String) = exec(FrontendChannel.ACTION, JcefBridge.jsString(actionId))
}
