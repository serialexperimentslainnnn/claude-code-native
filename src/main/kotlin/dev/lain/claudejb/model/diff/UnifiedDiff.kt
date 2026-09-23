package dev.lain.claudejb.model.diff

object UnifiedDiff {

    fun format(current: List<String>, proposed: List<String>, hunks: List<Hunk>, context: Int): String {
        val sb = StringBuilder()
        var from = 0
        while (from < hunks.size) {
            var to = from
            while (to + 1 < hunks.size && hunks[to + 1].start1 - hunks[to].end1 <= 2 * context) to++
            appendGroup(sb, current, proposed, hunks.subList(from, to + 1), context)
            from = to + 1
        }
        return sb.toString().trimEnd('\n')
    }

    private fun appendGroup(sb: StringBuilder, current: List<String>, proposed: List<String>, group: List<Hunk>, context: Int) {
        val first = group.first()
        val last = group.last()
        val lead = minOf(context, first.start1, first.start2)
        val trail = minOf(context, current.size - last.end1, proposed.size - last.end2).coerceAtLeast(0)
        val start1 = first.start1 - lead
        val start2 = first.start2 - lead
        val count1 = last.end1 + trail - start1
        val count2 = last.end2 + trail - start2
        sb.append("@@ -").append(position(start1, count1)).append(',').append(count1)
            .append(" +").append(position(start2, count2)).append(',').append(count2).append(" @@\n")
        var cursor = start1
        for (h in group) {
            for (i in cursor until h.start1) line(sb, ' ', current, i)
            for (i in h.start1 until h.end1) line(sb, '-', current, i)
            for (i in h.start2 until h.end2) line(sb, '+', proposed, i)
            cursor = h.end1
        }
        for (i in cursor until last.end1 + trail) line(sb, ' ', current, i)
    }

    private fun position(start: Int, count: Int): Int = if (count == 0) start else start + 1

    private fun line(sb: StringBuilder, mark: Char, lines: List<String>, index: Int) {
        sb.append(mark).append(lines.getOrElse(index) { "" }).append('\n')
    }
}
