package dev.lain.claudejb.view.feed

import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.session.transcript.ToolState
import dev.lain.claudejb.model.session.transcript.TranscriptEntry
import dev.lain.claudejb.model.session.transcript.TranscriptModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranscriptDeltasTest {

    private val model = TranscriptModel()

    private val deltas = TranscriptDeltas()

    private fun running(text: String): TranscriptEntry = model.add(Speaker.ASSISTANT, text, toolState = ToolState.RUNNING)

    @Test
    fun `a row the page has never seen goes whole`() {
        val entry = running("Hel")

        val split = deltas.split(listOf(entry to 0))

        assertEquals(1, split.rows.size)
        assertTrue(split.appends.isEmpty())
    }

    @Test
    fun `a running row whose text only grew goes as an append of the new part`() {
        val entry = running("Hel")
        deltas.split(listOf(entry to 0))
        model.append(entry, "lo")

        val split = deltas.split(listOf(entry to 0))

        assertTrue(split.rows.isEmpty())
        assertEquals(listOf(TranscriptDeltas.Append(entry.id, "lo")), split.appends)
    }

    @Test
    fun `a settled row that grew goes whole, since the page appends only to running rows`() {
        val entry = model.add(Speaker.ASSISTANT, "Hel")
        deltas.split(listOf(entry to 0))
        model.append(entry, "lo")

        val split = deltas.split(listOf(entry to 0))

        assertEquals(1, split.rows.size)
        assertTrue(split.appends.isEmpty())
        assertFalse(deltas.grew(entry, "!"))
    }

    @Test
    fun `an append already sent is not sent again`() {
        val entry = running("Hel")
        deltas.split(listOf(entry to 0))
        model.append(entry, "lo")
        assertTrue(deltas.grew(entry, "lo"))

        val split = deltas.split(listOf(entry to 0))

        assertTrue(split.rows.isEmpty() && split.appends.isEmpty())
    }

    @Test
    fun `a change beside the text sends the whole row`() {
        val entry = model.add(Speaker.TOOL, "x", toolUseId = "t1", toolState = ToolState.RUNNING)
        deltas.split(listOf(entry to 0))
        model.setToolState("t1", ToolState.FINISHED)

        assertEquals(1, deltas.split(listOf(entry to 0)).rows.size)
    }

    @Test
    fun `a rewritten text sends the whole row`() {
        val entry = running("Hello")
        deltas.split(listOf(entry to 0))
        model.replaceText(entry, "Help me")

        assertEquals(1, deltas.split(listOf(entry to 0)).rows.size)
    }

    @Test
    fun `a growth the page never saw the start of is not an append`() {
        assertFalse(deltas.grew(running("Hel"), "lo"))
    }

    @Test
    fun `after a reset every row goes whole again`() {
        val entry = running("Hel")
        deltas.split(listOf(entry to 0))
        deltas.reset()

        assertEquals(1, deltas.split(listOf(entry to 0)).rows.size)
    }
}
