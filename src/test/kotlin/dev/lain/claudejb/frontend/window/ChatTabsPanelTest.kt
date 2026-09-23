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

    private val toldHost = mutableListOf<String>()

    private val tabs = ChatTabsPanel({ toldHost += it.value }) { id -> FakeCard().also { built[id.value] = it } }

    private fun ref(id: String) = ChatRef(ChatId(id), id)

    private fun open() = tabs.chats().map { it.value }

    @AfterEach
    fun tearDown() = Disposer.dispose(tabs)

    @Test
    fun `the first sync opens every chat and shows the last without taking the focus`() {
        tabs.sync(listOf(ref("a"), ref("b")))

        assertEquals(listOf("a", "b"), open())
        assertEquals("b", tabs.selected?.value)
        assertEquals(listOf("b"), toldHost)
        assertEquals(0, built.getValue("b").focused)
    }

    @Test
    fun `closing the last chat shows its replacement even when the host does not ask to select it`() {
        tabs.sync(listOf(ref("a")))

        tabs.event(ChatEvent.Closed(ChatId("a")))
        assertNull(tabs.selected)
        tabs.event(ChatEvent.Opened(ref("b"), select = false))

        assertEquals(listOf("b"), open())
        assertEquals("b", tabs.selected?.value)
        assertTrue(built.getValue("b").component.isVisible)
    }

    @Test
    fun `a chat opened beside the one on screen is shown only once its page can draw`() {
        tabs.sync(listOf(ref("a")))

        tabs.event(ChatEvent.Opened(ref("b"), select = true))
        assertEquals("a", tabs.selected?.value)

        built.getValue("b").pageReady()
        assertEquals("b", tabs.selected?.value)
        assertEquals(1, built.getValue("b").focused)
    }

    @Test
    fun `a closed chat is hidden, out of the deck and disposed`() {
        tabs.sync(listOf(ref("a"), ref("b")))

        tabs.event(ChatEvent.Closed(ChatId("a")))

        val closed = built.getValue("a")
        assertTrue(closed.disposed)
        assertNull(closed.component.parent)
        assertFalse(closed.component.isVisible)
    }

    @Test
    fun `closing the chat on screen shows a survivor and tells the host`() {
        tabs.sync(listOf(ref("a"), ref("b")))

        tabs.event(ChatEvent.Closed(ChatId("b")))

        assertEquals("a", tabs.selected?.value)
        assertEquals(listOf("b", "a"), toldHost)
        assertTrue(built.getValue("a").component.isVisible)
    }

    @Test
    fun `a resync drops the chats the host no longer has and never builds one twice`() {
        tabs.sync(listOf(ref("a"), ref("b")))
        tabs.event(ChatEvent.Opened(ref("b"), select = false))

        tabs.sync(listOf(ref("b"), ref("c")))

        assertEquals(listOf("b", "c"), open())
        assertEquals(listOf("a", "b", "c"), built.keys.toList())
        assertTrue(built.getValue("a").disposed)
    }

    @Test
    fun `the host selecting a chat shows it and focuses its input`() {
        tabs.sync(listOf(ref("a"), ref("b")))

        tabs.event(ChatEvent.Selected(ChatId("a")))

        assertEquals("a", tabs.selected?.value)
        assertEquals(1, built.getValue("a").focused)
    }

    @Test
    fun `an event for a chat that is not open changes nothing`() {
        tabs.sync(listOf(ref("a")))

        tabs.event(ChatEvent.Selected(ChatId("ghost")))
        tabs.event(ChatEvent.Closed(ChatId("ghost")))

        assertEquals(listOf("a"), open())
        assertEquals("a", tabs.selected?.value)
    }
}
