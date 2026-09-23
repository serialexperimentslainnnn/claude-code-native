package dev.lain.claudejb.frontend.rpc

import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatRef

interface ChatListener {
    fun unavailable()

    fun sync(chats: List<ChatRef>)

    fun event(event: ChatEvent)
}
