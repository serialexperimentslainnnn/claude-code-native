package dev.lain.claudejb.model.mcp

object TextWindow {

    const val MAX_LINE = 2_000
    const val MAX_LIMIT = 2_000
    private const val ESCAPED_NEWLINE = 2
    private const val UNICODE_ESCAPE = 5

    class Slice(val lines: Int, val from: Int, val to: Int, val text: String)

    fun slice(text: CharSequence, offset: Int, limit: Int, budget: Int): Slice {
        val total = lineCount(text)
        val from = minOf(offset, total + 1)
        val out = StringBuilder()
        var at = startOf(text, from)
        var to = from - 1
        var used = 0
        while (to < total && to - from + 1 < limit) {
            val end = breakAt(text, at)
            val line = Clip.line(text.subSequence(at, end), MAX_LINE)
            val cost = cost(line)
            if (to >= from && used + cost > budget) break
            if (to >= from) out.append('\n')
            out.append(line)
            used += cost
            to++
            at = after(text, end)
        }
        return Slice(total, from, to, out.toString())
    }

    fun lineCount(text: CharSequence): Int {
        var count = 1
        var at = breakAt(text, 0)
        while (at < text.length) {
            count++
            at = breakAt(text, after(text, at))
        }
        return count
    }

    private fun startOf(text: CharSequence, line: Int): Int {
        var at = 0
        repeat(line - 1) { at = after(text, breakAt(text, at)) }
        return at
    }

    private fun breakAt(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
        return i
    }

    private fun after(text: CharSequence, end: Int): Int = when {
        end >= text.length -> text.length
        text[end] == '\r' && end + 1 < text.length && text[end + 1] == '\n' -> end + 2
        else -> end + 1
    }

    private fun cost(line: String): Int {
        var cost = ESCAPED_NEWLINE
        for (c in line) {
            cost += when {
                c == '\\' || c == '"' || c == '\t' -> 2
                c < ' ' -> UNICODE_ESCAPE + 1
                else -> 1
            }
        }
        return cost
    }
}
