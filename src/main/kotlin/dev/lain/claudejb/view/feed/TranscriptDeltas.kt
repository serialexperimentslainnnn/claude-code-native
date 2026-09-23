package dev.lain.claudejb.view.feed

import dev.lain.claudejb.model.session.transcript.ToolState
import dev.lain.claudejb.model.session.transcript.TranscriptEntry
import dev.lain.claudejb.view.payload.chat.JcefTranscriptPayload
import kotlinx.serialization.json.JsonObject

internal class TranscriptDeltas {

    data class Append(val id: Long, val delta: String)

    class Split(val rows: List<JsonObject>, val appends: List<Append>)

    private class Sent(val text: StringBuilder, val rest: JsonObject)

    private val sent = HashMap<Long, Sent>()

    fun split(items: List<Pair<TranscriptEntry, Int>>): Split {
        val rows = ArrayList<JsonObject>()
        val appends = ArrayList<Append>()
        for ((entry, order) in items) {
            val row = JcefTranscriptPayload.entryJson(entry, order)
            val text = entry.text
            val rest = JsonObject(row - TEXT)
            val previous = sent[entry.id]
            when {
                previous == null || previous.rest != rest -> rows += row

                text.contentEquals(previous.text) -> Unit

                entry.toolState == ToolState.RUNNING && text.length > previous.text.length && text.startsWith(previous.text) ->
                    appends += Append(entry.id, text.substring(previous.text.length))

                else -> rows += row
            }
            sent[entry.id] = Sent(StringBuilder(text), rest)
        }
        return Split(rows, appends)
    }

    fun grew(entry: TranscriptEntry, delta: String): Boolean {
        if (entry.toolState != ToolState.RUNNING) return false
        val previous = sent[entry.id] ?: return false
        previous.text.append(delta)
        return true
    }

    fun forget(ids: Collection<Long>) {
        ids.forEach(sent::remove)
    }

    fun reset() = sent.clear()

    private companion object {
        const val TEXT = "text"
    }
}
