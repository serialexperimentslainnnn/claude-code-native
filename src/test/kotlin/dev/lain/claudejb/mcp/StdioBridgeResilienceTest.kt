package dev.lain.claudejb.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path

class StdioBridgeResilienceTest {

    private fun frames(vararg header: String) = ByteArrayInputStream(header.joinToString("").toByteArray())

    @Test
    fun `a ten digit header is refused as a malformed frame, not a number format crash`() {
        assertThrows(IOException::class.java) { Frames.read(frames("9999999999\n")) }
    }

    @Test
    fun `a header of endless zeros is bounded`() {
        assertThrows(IOException::class.java) { Frames.read(frames("0".repeat(64) + "\n")) }
    }

    @Test
    fun `an empty header is malformed`() {
        assertThrows(IOException::class.java) { Frames.read(frames("\n")) }
    }

    @Test
    fun `a frame at the ceiling is still read by length`() {
        assertThrows(IOException::class.java) { Frames.read(frames("${Frames.MAX_FRAME_BYTES + 1}\n")) }
        assertEquals("ab", Frames.read(frames("0002\nab")))
    }

    @Test
    fun `an unreadable reply becomes a JSON-RPC error and the replies keep flowing`() {
        val wire = ByteArrayOutputStream()
        Frames.write(wire, "a: \"open")
        Frames.write(wire, Toon.encode(Json.parse("""{"jsonrpc":"2.0","id":7,"result":{}}""")))
        val stdout = ByteArrayOutputStream()
        val bridge = StdioBridge(Path.of("unused.sock"), PrintStream(stdout, true, Charsets.UTF_8))

        bridge.relayReplies(ByteArrayInputStream(wire.toByteArray()), null)

        val lines = stdout.toString(Charsets.UTF_8).trim().lines()
        assertEquals(2, lines.size, lines.toString())
        assertTrue(lines[0].contains("\"code\":-32603"), lines[0])
        assertEquals("""{"jsonrpc":"2.0","id":7,"result":{}}""", lines[1])
    }
}
