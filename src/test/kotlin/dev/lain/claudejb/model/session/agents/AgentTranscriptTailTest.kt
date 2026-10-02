package dev.lain.claudejb.model.session.agents

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class AgentTranscriptTailTest {

    @TempDir
    lateinit var dir: Path

    private val file get() = dir.resolve("agent-a.jsonl")

    private fun text(n: Int) = """{"type":"assistant","message":{"content":[{"type":"text","text":"line $n"}]}}"""

    private val closedTurn = """{"type":"assistant","message":{"stop_reason":"end_turn","content":[]}}"""

    private fun append(vararg lines: String) {
        Files.writeString(file, lines.joinToString("") { "$it\n" }, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + Files.size(file)))
    }

    @Test
    fun `only the lines appended since the last read are added`() {
        val tail = AgentTranscriptTail(file)
        append(text(1), text(2))
        tail.refresh()
        assertEquals(listOf("line 1", "line 2"), tail.entries.map { it.text })

        append(text(3))
        tail.refresh()

        assertEquals(listOf("line 1", "line 2", "line 3"), tail.entries.map { it.text })
        assertEquals(3, tail.recordCount)
    }

    @Test
    fun `a line still being written is shown once it parses and read again when it completes`() {
        val tail = AgentTranscriptTail(file)
        Files.writeString(file, text(1) + "\n" + text(2).take(20))
        tail.refresh()
        assertEquals(listOf("line 1"), tail.entries.map { it.text })

        Files.writeString(file, text(2).drop(20) + "\n", StandardOpenOption.APPEND)
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000))
        tail.refresh()

        assertEquals(listOf("line 1", "line 2"), tail.entries.map { it.text })
    }

    @Test
    fun `entries are capped while the record count keeps growing`() {
        val tail = AgentTranscriptTail(file)
        append(*Array(1_200) { text(it) })
        tail.refresh()

        assertEquals(500, tail.entries.size)
        assertEquals("line 1199", tail.entries.last().text)
        assertEquals(1_200, tail.recordCount)
    }

    @Test
    fun `a turn closed long before the window still makes a reopened agent resumed`() {
        val tail = AgentTranscriptTail(file)
        append(closedTurn, *Array(1_500) { text(it) }, """{"type":"user","message":{"content":[{"type":"tool_result","content":"x"}]}}""")
        tail.refresh()

        assertEquals(AgentEnding.Ending.RESUMED, tail.ending)
    }

    @Test
    fun `a rewritten transcript is read again from the start`() {
        val tail = AgentTranscriptTail(file)
        append(text(1), text(2), text(3))
        tail.refresh()

        Files.writeString(file, text(9) + "\n")
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 9_000))
        tail.refresh()

        assertEquals(listOf("line 9"), tail.entries.map { it.text })
        assertTrue(tail.recordCount >= 1)
    }
}
