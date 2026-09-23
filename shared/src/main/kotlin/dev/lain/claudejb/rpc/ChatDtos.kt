package dev.lain.claudejb.rpc

import kotlinx.serialization.Serializable

@Serializable
data class ChatId(val value: String)

@Serializable
data class ChatRef(val id: ChatId, val title: String)

@Serializable
data class PagePush(val method: String, val json: String)

@Serializable
sealed interface ChatEvent {
    @Serializable
    data class Opened(val chat: ChatRef, val select: Boolean) : ChatEvent

    @Serializable
    data class Closed(val id: ChatId) : ChatEvent

    @Serializable
    data class Selected(val id: ChatId) : ChatEvent

    @Serializable
    data class Renamed(val chat: ChatRef) : ChatEvent
}
