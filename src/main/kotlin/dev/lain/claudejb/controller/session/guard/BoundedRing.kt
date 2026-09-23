package dev.lain.claudejb.controller.session.guard

internal class BoundedRing<T>(private val capacity: Int) {

    private val items = ArrayDeque<T>(capacity)

    @Synchronized
    fun add(item: T) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(item)
    }

    @Synchronized
    fun replaceAll(replacement: List<T>) {
        items.clear()
        items.addAll(replacement.takeLast(capacity))
    }

    @Synchronized
    fun isEmpty(): Boolean = items.isEmpty()

    @Synchronized
    fun filter(predicate: (T) -> Boolean): List<T> = items.filter(predicate)
}
