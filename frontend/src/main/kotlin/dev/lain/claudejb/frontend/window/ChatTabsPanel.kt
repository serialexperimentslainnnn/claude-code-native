package dev.lain.claudejb.frontend.window

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
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
    private val onDesync: () -> Unit,
    private val newCard: (ChatId) -> ChatCard,
) : JBPanel<ChatTabsPanel>(BorderLayout()), Disposable, ChatListener {

    private val cards = CardLayout()

    private val deck = JPanel(cards)

    private val open = LinkedHashMap<ChatId, ChatCard>()

    var selected: ChatId? = null
        private set

    private var awaited: ChatId? = null

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

    override fun event(event: ChatEvent) {
        when (event) {
            is ChatEvent.Listed -> listed(event.chats, event.selected)
            is ChatEvent.Opened -> opened(event.chat.id, event.select)
            is ChatEvent.Closed -> remove(event.id)
            is ChatEvent.Selected -> selectedByHost(event.id)
            is ChatEvent.Renamed -> Unit
        }
    }

    fun focusIfSelected(id: ChatId) {
        if (selected == id) open[id]?.focus()
    }

    private fun selectedByHost(id: ChatId) {
        awaited = null
        if (!show(id, focus = true)) desync(id)
    }

    private fun listed(chats: List<ChatRef>, shown: ChatId?) {
        awaited = null
        val listed = chats.mapTo(HashSet()) { it.id }
        open.keys.filterNot { it in listed }.forEach(::remove)
        chats.forEach { add(it.id) }
        (shown ?: chats.lastOrNull()?.id)?.let { show(it, focus = false) }
    }

    private fun opened(id: ChatId, select: Boolean) {
        add(id)
        if (select) awaited = id
        when {
            selected == null -> show(id, focus = select)
            select -> open[id]?.whenReady { if (awaited == id) show(id, focus = true) }
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
        if (selected == id) selected = null
        card.component.isVisible = false
        deck.remove(card.component)
        Disposer.dispose(card)
        deck.revalidate()
        deck.repaint()
    }

    private fun desync(id: ChatId) {
        log.warn("Claude Code host selected chat '${id.value}', which this window does not have; asking for the full list again")
        onDesync()
    }

    override fun dispose() = Unit

    private companion object {
        private val log = logger<ChatTabsPanel>()

        const val UNAVAILABLE =
            "<html>The Claude Code chat runs on the host IDE. Install and enable the Claude Code plugin there " +
                "to use it from this client.</html>"
    }
}
