package dev.lain.claudejb.model.session.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReverseLinesTest {

    @TempDir
    lateinit var dir: Path

    private fun newestFirst(bytes: ByteArray): List<String> {
        val file = dir.resolve("t.jsonl")
        Files.write(file, bytes)
        return ReverseLines.read(file) { it.toList() }
    }

    @Test
    fun `lines come back newest first, with the trailing newline as an empty line`() {
        assertEquals(listOf("", "c", "b", "a"), newestFirst("a\nb\nc\n".toByteArray()))
        assertEquals(listOf("c", "b", "a"), newestFirst("a\nb\nc".toByteArray()))
    }

    @Test
    fun `lines longer than a block are joined across blocks`() {
        val long = "x".repeat(200_000) + "é" + "y".repeat(70_000)
        val short = "z".repeat(10)

        assertEquals(listOf(short, long, short), newestFirst("$short\n$long\n$short".toByteArray()))
    }

    @Test
    fun `invalid UTF-8 is replaced instead of losing the line`() {
        val bytes = "ok\n".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28) + "tail".toByteArray()

        val lines = newestFirst(bytes)

        assertEquals(2, lines.size)
        assertEquals("�(tail", lines[0])
        assertEquals("ok", lines[1])
    }

    @Test
    fun `an empty file has no lines`() {
        assertEquals(emptyList<String>(), newestFirst(ByteArray(0)))
    }
}
