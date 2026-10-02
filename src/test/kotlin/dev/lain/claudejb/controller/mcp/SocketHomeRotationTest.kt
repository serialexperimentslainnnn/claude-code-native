package dev.lain.claudejb.controller.mcp

import dev.lain.claudejb.mcp.StdioBridge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SocketHomeRotationTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `a reader never sees a missing or empty token while it rotates`() {
        val home = SocketHome.create(listOf(tmp))
        home.writeToken("token-0")
        val file = home.dir.resolve(StdioBridge.TOKEN_FILE)
        val stop = AtomicBoolean()
        val bad = mutableListOf<String>()
        val reader = thread {
            while (!stop.get()) {
                val seen = runCatching { Files.readString(file) }.getOrElse { "<${it.javaClass.simpleName}>" }
                if (!seen.startsWith("token-")) synchronized(bad) { bad += seen }
            }
        }
        repeat(2_000) { home.writeToken("token-$it") }
        stop.set(true)
        reader.join()
        assertTrue(bad.isEmpty()) { bad.take(5).toString() }
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        home.remove()
    }
}
