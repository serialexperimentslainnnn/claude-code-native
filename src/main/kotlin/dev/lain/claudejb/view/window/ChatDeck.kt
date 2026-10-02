package dev.lain.claudejb.view.window

import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import java.util.concurrent.CopyOnWriteArrayList

internal class ChatDeck<T : Any>(
    private val idOf: (T) -> ChatId,
    private val refOf: (T) -> ChatRef,
) {

    private val items = CopyOnWriteArrayList<T>()
    private val listeners = CopyOnWriteArrayList<(ChatEvent) -> Unit>()

    @Volatile
    var selected: T? = null
        private set

    fun all(): List<T> = items.toList()

    fun isEmpty(): Boolean = items.isEmpty()

    fun find(id: ChatId): T? = items.firstOrNull { idOf(it) == id }

    fun add(item: T, select: Boolean): Boolean {
        items += item
        val shown = select || selected == null
        if (shown) selected = item
        fire(ChatEvent.Opened(refOf(item), shown))
        return shown
    }

    fun select(id: ChatId): T? {
        val item = find(id) ?: return null
        selected = item
        fire(ChatEvent.Selected(id))
        return item
    }

    fun remove(id: ChatId): T? {
        val item = find(id) ?: return null
        items.remove(item)
        if (selected === item) {
            selected = null
            items.firstOrNull()?.let { select(idOf(it)) }
        }
        fire(ChatEvent.Closed(id))
        return item
    }

    fun renamed(item: T) {
        if (item in items) fire(ChatEvent.Renamed(refOf(item)))
    }

    fun subscribe(listener: (ChatEvent) -> Unit): () -> Unit {
        listener(ChatEvent.Listed(items.map(refOf), selected?.let(idOf)))
        listeners += listener
        return { listeners -= listener }
    }

    fun clear() {
        listeners.clear()
        items.clear()
        selected = null
    }

    private fun fire(event: ChatEvent) = listeners.forEach { it(event) }
}
