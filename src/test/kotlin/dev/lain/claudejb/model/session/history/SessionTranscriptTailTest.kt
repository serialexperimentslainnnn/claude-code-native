package dev.lain.claudejb.model.session.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionTranscriptTailTest {

    private fun user(text: String) = """{"type":"user","message":{"role":"user","content":"$text"}}"""

    private fun call(id: String, command: String) =
        """{"type":"assistant","message":{"content":[{"type":"tool_use","id":"$id","name":"Bash","input":{"command":"$command"}}]}}"""

    private fun result(id: String, text: String, error: Boolean = false) =
        """{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"$id","is_error":$error,"content":"$text"}]}}"""

    private fun ownCall(id: String) =
        """{"type":"assistant","message":{"content":[{"type":"tool_use","id":"$id","name":"mcp__vcs__run","input":{"tool":"git_status","args":{}}}]}}"""

    private fun conversation(): List<String> = buildList {
        repeat(60) { i ->
            add(user("prompt $i"))
            add(call("t$i", "echo $i"))
            add(result("t$i", "out $i", error = i % 7 == 0))
            add(ownCall("o$i"))
            add(result("o$i", "branch: main"))
        }
        add(call("pending", "sleep 1"))
    }

    @Test
    fun `reading from the end yields exactly the capped window of a full read`() {
        val lines = conversation()
        for (cap in listOf(1, 2, 3, 7, 50, 199, 200, 1000)) {
            val full = SessionTranscriptReader.entriesOf(SessionTranscriptReader.parseRecords(lines), cap)
            assertEquals(full, SessionTranscriptReader.parseEntries(lines, cap)) { "cap $cap" }
        }
    }

    @Test
    fun `the tail window keeps the in-flight mark and drops outputs whose call fell outside it`() {
        val entries = SessionTranscriptReader.parseEntries(conversation(), 4)

        assertTrue(entries.last().inFlight)
        assertTrue(entries.none { it.speaker == "TOOL_OUTPUT" && entries.none { t -> t.speaker == "TOOL" && t.toolUseId == it.toolUseId } })
    }

    @Test
    fun `own tool results in the window are decoded`() {
        val entries = SessionTranscriptReader.parseEntries(conversation().dropLast(1), 2)

        assertEquals(listOf("TOOL", "TOOL_OUTPUT"), entries.map { it.speaker })
        assertEquals("toon", entries.last().meta)
    }
}
