package dev.lain.claudejb.rpc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatContractTest {

    private val chat = ChatRef(ChatId("c-1"), "First chat")

    private fun <T> roundTrip(serializer: KSerializer<T>, value: T) {
        assertEquals(value, Json.decodeFromString(serializer, Json.encodeToString(serializer, value)))
    }

    @Test
    fun `every value the contract carries survives the wire unchanged`() {
        roundTrip(ChatId.serializer(), ChatId("c-1"))
        roundTrip(ChatRef.serializer(), chat)
        roundTrip(PagePush.serializer(), PagePush("batch", "[{\"id\":1}]"))
        roundTrip(TerminalLaunch.serializer(), TerminalLaunch("/work", "Claude login", "claude auth login"))
        roundTrip(TerminalLaunch.serializer(), TerminalLaunch(null, "Claude", "claude"))
        roundTrip(
            GearItem.serializer(),
            GearItem(
                listOf(3),
                "Git Operations",
                enabled = true,
                children = listOf(
                    GearItem(listOf(3, 0), "Pull", enabled = false),
                    GearItem(listOf(3, 1), "", enabled = false, separator = true),
                ),
            ),
        )
    }

    @Test
    fun `every chat event keeps its kind across the wire`() {
        val events = listOf(
            ChatEvent.Opened(chat, select = true),
            ChatEvent.Opened(chat, select = false),
            ChatEvent.Closed(chat.id),
            ChatEvent.Selected(chat.id),
            ChatEvent.Renamed(ChatRef(chat.id, "Renamed")),
        )
        events.forEach { roundTrip(ChatEvent.serializer(), it) }
    }

    @Test
    fun `the frontend keeps its own pushes and forwards everything else to the page`() {
        listOf(
            FrontendChannel.COPY,
            FrontendChannel.BROWSE,
            FrontendChannel.VIBE,
            FrontendChannel.FOCUS,
            FrontendChannel.TERMINAL,
            FrontendChannel.ACTION,
        ).forEach { assertTrue(FrontendChannel.isFrontendPush(it), it) }
        listOf("batch", "append", "theme", FrontendChannel.ATTACH_IMAGE, "").forEach {
            assertFalse(FrontendChannel.isFrontendPush(it), it)
        }
    }

    @Test
    fun `only the clipboard reads stay on the client`() {
        assertEquals(setOf("pasteClipboard", "pasteClipboardImage"), FrontendChannel.clientMessages)
    }
}
