fun inline(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        .replace(Regex("`(.+?)`"), "<code>$1</code>")

fun latestSection(lines: List<String>): List<String> {
    val start = lines.indexOfFirst { it.startsWith("## v") }
    if (start < 0) return emptyList()
    val next = lines.drop(start + 1).indexOfFirst { it.startsWith("## v") }
    return lines.subList(start, if (next < 0) lines.size else start + 1 + next)
}

fun releaseNotesHtml(lines: List<String>): String {
    val html = StringBuilder()
    var inList = false

    fun closeList() {
        if (inList) html.append("</ul>")
        inList = false
    }

    for (line in lines.map { it.trim() }) {
        when {
            line.startsWith("## v") -> {
                html.append("<p><b>").append(inline(line.removePrefix("## ").trim())).append("</b></p>")
            }

            line == "---" || line.isEmpty() -> {
                closeList()
            }

            line.startsWith("- ") -> {
                if (!inList) html.append("<ul>")
                inList = true
                html.append("<li>").append(inline(line.removePrefix("- ").trim())).append("</li>")
            }

            else -> {
                closeList()
                html.append("<p>").append(inline(line)).append("</p>")
            }
        }
    }
    closeList()
    return html.toString()
}

val notes = file("RELEASE_NOTES.md")
val section = if (notes.exists()) latestSection(notes.readLines()) else emptyList()
extra["changeNotesHtml"] = if (section.isEmpty()) "See RELEASE_NOTES.md." else releaseNotesHtml(section)
