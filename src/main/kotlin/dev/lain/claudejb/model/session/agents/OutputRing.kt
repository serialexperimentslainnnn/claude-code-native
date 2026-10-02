package dev.lain.claudejb.model.session.agents

class OutputRing(private val cap: Int, initial: String = "") {

    private val buffer = StringBuilder(initial.takeLast(cap))

    private var cached: String? = null

    @Volatile var version = 0
        private set

    @Synchronized
    fun append(text: String): Boolean {
        if (text.isEmpty()) return false
        buffer.append(text)
        if (buffer.length > 2 * cap) buffer.delete(0, buffer.length - cap)
        cached = null
        version++
        return true
    }

    @Synchronized
    fun text(): String = cached ?: (if (buffer.length > cap) buffer.substring(buffer.length - cap) else buffer.toString())
        .also { cached = it }
}
