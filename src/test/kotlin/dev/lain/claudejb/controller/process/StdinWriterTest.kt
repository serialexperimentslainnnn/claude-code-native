package dev.lain.claudejb.controller.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StdinWriterTest {

    @Test
    fun `a pipe that stops draining never blocks the caller`() {
        val release = CountDownLatch(1)
        val stalled = object : OutputStream() {
            override fun write(b: Int) {
                release.await()
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        val writer = StdinWriter(executor)

        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            repeat(LINES) { writer.enqueue(stalled, "{\"type\":\"control_response\"}") }
        }

        release.countDown()
        drain(executor)
    }

    @Test
    fun `lines reach the pipe whole, in order, one per line`() {
        val sink = ByteArrayOutputStream()
        val executor = Executors.newSingleThreadExecutor()
        val writer = StdinWriter(executor)

        listOf("first", "è second", "third").forEach { writer.enqueue(sink, it) }
        drain(executor)

        assertEquals("first\nè second\nthird\n", sink.toString(Charsets.UTF_8))
    }

    @Test
    fun `a broken pipe does not stop the lines queued after it`() {
        val broken = object : OutputStream() {
            override fun write(b: Int) = throw IOException("pipe closed")
        }
        val sink = ByteArrayOutputStream()
        val executor = Executors.newSingleThreadExecutor()
        val writer = StdinWriter(executor)

        writer.enqueue(broken, "lost")
        writer.enqueue(sink, "kept")
        drain(executor)

        assertEquals("kept\n", sink.toString(Charsets.UTF_8))
    }

    private fun drain(executor: ExecutorService) {
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)) { "the writer never drained its queue" }
    }

    private companion object {
        const val LINES = 64
    }
}
