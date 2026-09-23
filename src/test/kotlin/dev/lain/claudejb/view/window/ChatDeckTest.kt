package dev.lain.claudejb.view.window

import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatDeckTest {

    private val deck = ChatDeck<ChatRef>({ it.id }, { it })

    private val events = mutableListOf<ChatEvent>()

    private fun chat(name: String) = ChatRef(ChatId(name), name)

    @Test
    fun `the first chat is shown even when it was not asked to be`() {
        deck.subscribe { events += it }
        val a = chat("a")

        assertTrue(deck.add(a, select = false))

        assertEquals(a, deck.selected)
        assertEquals(listOf<ChatEvent>(ChatEvent.Opened(a, true)), events)
    }

    @Test
    fun `a chat restored in the background does not steal the selection`() {
        val a = chat("a")
        deck.add(a, select = true)
        deck.add(chat("b"), select = false)

        assertEquals(a, deck.selected)
    }

    @Test
    fun `closing the selected chat selects a survivor before announcing the close`() {
        val a = chat("a")
        val b = chat("b")
        deck.add(a, select = true)
        deck.add(b, select = false)
        deck.subscribe { events += it }
        events.clear()

        deck.remove(a.id)

        assertEquals(b, deck.selected)
        assertEquals(listOf(ChatEvent.Selected(b.id), ChatEvent.Closed(a.id)), events)
    }

    @Test
    fun `closing the last chat leaves nothing selected`() {
        val a = chat("a")
        deck.add(a, select = true)

        deck.remove(a.id)

        assertTrue(deck.isEmpty())
        assertNull(deck.selected)
    }

    @Test
    fun `closing or selecting an unknown chat does nothing`() {
        deck.add(chat("a"), select = true)
        deck.subscribe { events += it }
        events.clear()

        assertNull(deck.remove(ChatId("nope")))
        assertNull(deck.select(ChatId("nope")))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a new subscriber is told about every open chat and which one is shown`() {
        val a = chat("a")
        val b = chat("b")
        deck.add(a, select = true)
        deck.add(b, select = false)

        deck.subscribe { events += it }

        assertEquals(listOf<ChatEvent>(ChatEvent.Opened(a, true), ChatEvent.Opened(b, false)), events)
    }

    @Test
    fun `an unsubscribed listener hears nothing more`() {
        val stop = deck.subscribe { events += it }
        stop()

        deck.add(chat("a"), select = true)

        assertTrue(events.isEmpty())
    }
}
