package dev.lain.claudejb.frontend.rpc

import dev.lain.claudejb.rpc.ChatEvent

interface ChatListener {
    fun unavailable()

    fun event(event: ChatEvent)
}
