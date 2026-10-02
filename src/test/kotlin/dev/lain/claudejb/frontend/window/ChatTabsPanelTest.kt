package dev.lain.claudejb.frontend.window

import com.intellij.openapi.util.Disposer
import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JPanel

class ChatTabsPanelTest {

    private class FakeCard : ChatCard {
        override val component: JComponent = JPanel()
        var focused = 0
        var disposed = false

        override fun focus() {
            focused++
        }

        override fun focusTarget(): JComponent = component

        val waiting = mutableListOf<() -> Unit>()

        override fun whenReady(block: () -> Unit) {
            waiting += block
        }

        fun pageReady() = waiting.toList().also { waiting.clear() }.forEach { it() }

        override fun dispose() {
            disposed = true
        }
    }

    private val built = LinkedHashMap<String, FakeCard>()

    private var resyncs = 0

    private val tabs = ChatTabsPanel({ resyncs++ }) { id -> FakeCard().also { built[id.value] = it } }

    private fun ref(id: String) = ChatRef(ChatId(id), id)

    private fun listed(vararg ids: String, selected: String? = null) =
        tabs.event(ChatEvent.Listed(ids.map(::ref), selected?.let(::ChatId)))

    private fun open() = tabs.chats().map { it.value }

    @AfterEach
    fun tearDown() = Disposer.dispose(tabs)

    @Test
    fun `a listing opens every chat and shows the one the host selected without taking the focus`() {
        listed("a", "b", selected = "a")

        assertEquals(listOf("a", "b"), open())
        assertEquals("a", tabs.selected?.value)
        assertEquals(0, built.getValue("a").focused)
    }

    @Test
    fun `a new listing drops the chats the host no longer has and never builds one twice`() {
        listed("a", "b", selected = "a")

        listed("b", "c", selected = "c")

        assertEquals(listOf("b", "c"), open())
        assertEquals(listOf("a", "b", "c"), built.keys.toList())
        assertTrue(built.getValue("a").disposed)
        assertEquals("c", tabs.selected?.value)
    }

    @Test
    fun `closing the chat on screen follows the host's choice of survivor and closes nothing else`() {
        listed("a", "b", "c", selected = "c")

        tabs.event(ChatEvent.Selected(ChatId("a")))
        tabs.event(ChatEvent.Closed(ChatId("c")))

        assertEquals(listOf("a", "b"), open())
        assertEquals("a", tabs.selected?.value)
        assertTrue(built.getValue("a").component.isVisible)
        assertFalse(built.getValue("b").disposed)
    }

    @Test
    fun `closing a chat that is not on screen leaves the one on screen alone`() {
        listed("a", "b", selected = "b")

        tabs.event(ChatEvent.Closed(ChatId("a")))

        assertEquals(listOf("b"), open())
        assertEquals("b", tabs.selected?.value)
        val closed = built.getValue("a")
        assertTrue(closed.disposed)
        assertNull(closed.component.parent)
    }

    @Test
    fun `closing the last chat shows the replacement the host opens`() {
        listed("a", selected = "a")

        tabs.event(ChatEvent.Closed(ChatId("a")))
        assertNull(tabs.selected)
        tabs.event(ChatEvent.Opened(ref("b"), select = true))

        assertEquals(listOf("b"), open())
        assertEquals("b", tabs.selected?.value)
    }

    @Test
    fun `a chat opened beside the one on screen is shown only once its page can draw`() {
        listed("a", selected = "a")

        tabs.event(ChatEvent.Opened(ref("b"), select = true))
        assertEquals("a", tabs.selected?.value)

        built.getValue("b").pageReady()
        assertEquals("b", tabs.selected?.value)
        assertEquals(1, built.getValue("b").focused)
    }

    @Test
    fun `a chat still drawing does not take the screen once the host has selected another`() {
        listed("a", "c", selected = "a")

        tabs.event(ChatEvent.Opened(ref("b"), select = true))
        tabs.event(ChatEvent.Selected(ChatId("c")))
        built.getValue("b").pageReady()

        assertEquals("c", tabs.selected?.value)
    }

    @Test
    fun `a chat restored in the background does not take the screen`() {
        listed("a", selected = "a")

        tabs.event(ChatEvent.Opened(ref("b"), select = false))

        assertEquals("a", tabs.selected?.value)
    }

    @Test
    fun `the host selecting a chat this window lacks asks for the full list again`() {
        listed("a", selected = "a")

        tabs.event(ChatEvent.Selected(ChatId("ghost")))

        assertEquals(1, resyncs)
        assertEquals("a", tabs.selected?.value)
    }

    @Test
    fun `focus reaches only the chat on screen`() {
        listed("a", "b", selected = "b")

        tabs.focusIfSelected(ChatId("a"))
        tabs.focusIfSelected(ChatId("b"))

        assertEquals(0, built.getValue("a").focused)
        assertEquals(1, built.getValue("b").focused)
        assertEquals("b", tabs.selected?.value)
    }
}
