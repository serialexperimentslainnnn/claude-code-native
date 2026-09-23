package dev.lain.claudejb.model.mcp

object Clip {

    const val ELLIPSIS = "…"

    fun line(text: CharSequence, max: Int): String =
        if (text.length <= max) text.toString() else text.subSequence(0, boundary(text, max)).toString() + ELLIPSIS

    fun boundary(text: CharSequence, end: Int): Int =
        if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end - 1 else end
}
