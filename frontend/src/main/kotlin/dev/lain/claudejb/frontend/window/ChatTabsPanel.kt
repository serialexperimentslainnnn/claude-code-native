package dev.lain.claudejb.frontend.window

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import dev.lain.claudejb.frontend.rpc.ChatListener
import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class ChatTabsPanel(
    private val onSelect: (ChatId) -> Unit,
    private val newCard: (ChatId) -> ChatCard,
) : JBPanel<ChatTabsPanel>(BorderLayout()), Disposable, ChatListener {

    private val cards = CardLayout()

    private val deck = JPanel(cards)

    private val open = LinkedHashMap<ChatId, ChatCard>()

    var selected: ChatId? = null
        private set

    init {
        add(deck, BorderLayout.CENTER)
    }

    fun chats(): List<ChatId> = open.keys.toList()

    fun focusTarget(): JComponent? = selected?.let { open[it] }?.focusTarget()

    override fun unavailable() {
        removeAll()
        add(JBLabel(UNAVAILABLE).apply { border = JBUI.Borders.empty(16) }, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    override fun sync(chats: List<ChatRef>) {
        val listed = chats.mapTo(HashSet()) { it.id }
        open.keys.filterNot { it in listed }.forEach(::remove)
        chats.forEach { add(it.id) }
        if (selected == null) chats.lastOrNull()?.let { if (show(it.id, focus = false)) onSelect(it.id) }
    }

    override fun event(event: ChatEvent) {
        when (event) {
            is ChatEvent.Opened -> opened(event.chat.id, event.select)
            is ChatEvent.Closed -> remove(event.id)
            is ChatEvent.Selected -> show(event.id, focus = true)
            is ChatEvent.Renamed -> Unit
        }
    }

    fun select(id: ChatId) {
        if (show(id, focus = true)) onSelect(id)
    }

    fun focus(id: ChatId) {
        show(id, focus = true)
    }

    private fun opened(id: ChatId, select: Boolean) {
        add(id)
        when {
            selected == null -> show(id, focus = select)
            select -> open[id]?.whenReady { if (open.keys.lastOrNull() == id) show(id, focus = true) }
        }
    }

    private fun add(id: ChatId) {
        if (id in open) return
        val card = newCard(id)
        Disposer.register(this, card)
        open[id] = card
        deck.add(card.component, id.value)
    }

    private fun show(id: ChatId, focus: Boolean): Boolean {
        val card = open[id] ?: return false
        selected = id
        cards.show(deck, id.value)
        deck.revalidate()
        deck.repaint()
        if (focus) card.focus()
        return true
    }

    private fun remove(id: ChatId) {
        val card = open.remove(id) ?: return
        if (selected == id) {
            selected = null
            open.keys.lastOrNull()?.let(::select)
        }
        card.component.isVisible = false
        deck.remove(card.component)
        Disposer.dispose(card)
        deck.revalidate()
        deck.repaint()
    }

    override fun dispose() = Unit

    private companion object {
        const val UNAVAILABLE =
            "<html>The Claude Code chat runs on the host IDE. Install and enable the Claude Code plugin there " +
                "to use it from this client.</html>"
    }
}
