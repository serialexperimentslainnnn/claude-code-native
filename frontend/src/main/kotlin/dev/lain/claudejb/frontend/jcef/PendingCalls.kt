package dev.lain.claudejb.frontend.jcef

internal class PendingCalls(private val maxDeltas: Int = MAX_DELTAS) {

    class Drained(val calls: List<String>, val overflowed: Boolean)

    private val calls = LinkedHashMap<Any, String>()

    private var deltas = 0

    private var overflowed = false

    fun add(key: String?, js: String) {
        if (key != null) {
            calls.remove(key)
            calls[key] = js
            return
        }
        if (overflowed) return
        if (deltas < maxDeltas) {
            calls[Any()] = js
            deltas++
            return
        }
        calls.keys.removeIf { it !is String }
        deltas = 0
        overflowed = true
    }

    fun drain(): Drained {
        val drained = Drained(calls.values.toList(), overflowed)
        calls.clear()
        deltas = 0
        overflowed = false
        return drained
    }

    companion object {
        const val MAX_DELTAS = 512
    }
}
