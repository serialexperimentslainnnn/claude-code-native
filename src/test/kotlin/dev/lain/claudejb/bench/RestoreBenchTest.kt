package dev.lain.claudejb.bench

import dev.lain.claudejb.model.session.history.SessionTranscriptReader
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("bench")
class RestoreBenchTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `a twenty megabyte session is read back to the capped entry list`() {
        val file = dir.resolve("session.jsonl")
        TranscriptFixture.write(file, TARGET_BYTES, SEED)
        var entries = 0
        val median = BenchClock.medianMillis(WARMUPS, RUNS) {
            val lines = Files.readAllLines(file)
            entries = SessionTranscriptReader.parseEntries(lines, SessionTranscriptReader.DEFAULT_RESTORE_CAP).size
        }
        assertTrue(entries in 1..SessionTranscriptReader.DEFAULT_RESTORE_CAP)
        BenchReport.record("restore_20mb", median, "ms")
    }

    private companion object {
        const val TARGET_BYTES = 20L * 1024 * 1024
        const val SEED = 20
        const val WARMUPS = 2
        const val RUNS = 5
    }
}
