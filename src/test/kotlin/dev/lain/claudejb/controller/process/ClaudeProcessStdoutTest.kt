package dev.lain.claudejb.controller.process

import dev.lain.claudejb.model.protocol.ClaudeEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ClaudeProcessStdoutTest {

    @TempDir
    lateinit var dir: Path

    private val events = mutableListOf<ClaudeEvent>()

    private fun consumer(): (String) -> Unit {
        val process = ClaudeProcess(File("claude"), dir.toFile(), emptyList(), onEvent = { events += it }, onTerminated = {})
        val method = ClaudeProcess::class.java.getDeclaredMethod("consumeStdout", String::class.java)
        method.isAccessible = true
        return { chunk -> method.invoke(process, chunk) }
    }

    @Test
    fun `a line split over many chunks is parsed once, when its newline arrives`() {
        val consume = consumer()
        val line = """{"type":"keep_alive"}""" + "\n" + """{"type":"something_new","subtype":"x"}""" + "\n"

        line.chunked(3).forEach(consume)

        assertEquals(1, events.size)
        assertEquals("something_new", (events.single() as ClaudeEvent.Other).type)
    }

    @Test
    fun `blank and carriage-returned lines are handled without a copy of the line`() {
        val consume = consumer()

        consume("   \n\r\n")
        consume("  {\"type\":\"something_new\"}\r\n")

        assertEquals(listOf("something_new"), events.map { (it as ClaudeEvent.Other).type })
    }

    @Test
    fun `several lines in one chunk come out in order`() {
        val consume = consumer()

        consume("{\"type\":\"a_type\"}\n{\"type\":\"b_type\"}\n{\"type\":\"c_")
        consume("type\"}\n")

        assertEquals(listOf("a_type", "b_type", "c_type"), events.map { (it as ClaudeEvent.Other).type })
    }
}
