package dev.lain.claudejb.bench

import dev.lain.claudejb.controller.process.ClaudeProcess
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

@Tag("bench")
class StdoutParseBenchTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `a five megabyte stdout line arrives in eight kilobyte chunks`() {
        val chunks = line().chunked(CHUNK_CHARS)
        var events = 0
        val median = BenchClock.medianMillis(WARMUPS, RUNS) {
            val process =
                ClaudeProcess(File("claude"), dir.toFile(), emptyList(), onEvent = { events++ }, onTerminated = {})
            val consume = consumerOf(process)
            chunks.forEach(consume)
        }
        assertTrue(events > 0)
        BenchReport.record("stdout_parse", median, "ms")
    }

    private fun consumerOf(process: ClaudeProcess): (String) -> Unit {
        val method = ClaudeProcess::class.java.getDeclaredMethod("consumeStdout", String::class.java)
        method.isAccessible = true
        return { chunk -> method.invoke(process, chunk) }
    }

    private fun line(): String {
        val random = Random(SEED)
        val text = buildString(LINE_CHARS) {
            while (length < LINE_CHARS) append(BenchText.word(random)).append(' ')
        }
        return "{\"type\":\"assistant\",\"session_id\":\"bench\",\"message\":{\"id\":\"msg_bench\"," +
            "\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"$text\"}]}}\n"
    }

    private companion object {
        const val LINE_CHARS = 5 * 1024 * 1024
        const val CHUNK_CHARS = 8 * 1024
        const val SEED = 5
        const val WARMUPS = 2
        const val RUNS = 5
    }
}
