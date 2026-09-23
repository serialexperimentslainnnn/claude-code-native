package dev.lain.claudejb.frontend.jcef

internal object PageMinifier {

    private val HTML_COMMENT = Regex("<!--(?!(?:CSP|CSS|LIBS|APP)-->)[\\s\\S]*?-->")

    fun shell(html: String): String =
        html.replace(HTML_COMMENT, "").lineSequence().filter { it.isNotBlank() }.joinToString("\n")

    fun css(source: String): String =
        withoutCssComments(source).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    fun isolated(script: String): String =
        "try{\n" + script + "\n}catch(e){setTimeout(function(){throw e})}"

    private fun withoutCssComments(source: String): String {
        val out = StringBuilder(source.length)
        var quote: Char? = null
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                quote != null -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < source.length) {
                        out.append(source[i + 1])
                        i++
                    } else if (c == quote) {
                        quote = null
                    }
                }

                c == '"' || c == '\'' -> {
                    quote = c
                    out.append(c)
                }

                c == '/' && source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i + 2)
                    i = if (end < 0) source.length else end + 1
                }

                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
