package dev.lain.claudejb.bench

import dev.lain.claudejb.model.session.agents.AgentMeta
import dev.lain.claudejb.model.session.agents.AgentRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("bench")
class AgentScanBenchTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `a ten megabyte agent transcript is scanned by a fresh registry`() {
        Files.writeString(
            dir.resolve("${AgentMeta.FILE_PREFIX}$AGENT${AgentMeta.META_SUFFIX}"),
            """{"agentType":"general-purpose","description":"Bench agent","spawnDepth":1}""",
        )
        TranscriptFixture.write(dir.resolve(AgentMeta.transcriptFile(AGENT)), TARGET_BYTES, SEED)
        var admitted = emptyList<String>()
        val median = BenchClock.medianMillis(WARMUPS, RUNS) {
            val registry = AgentRegistry(subagentsDir = { dir }, now = { CLOCK }, runStartedAtMillis = CLOCK)
            registry.preAdmit(listOf(AGENT))
            admitted = registry.scan()
        }
        assertEquals(listOf(AGENT), admitted)
        BenchReport.record("agent_scan_10mb", median, "ms")
    }

    private companion object {
        const val AGENT = "bench"
        const val TARGET_BYTES = 10L * 1024 * 1024
        const val SEED = 10
        const val CLOCK = 1_000_000_000L
        const val WARMUPS = 2
        const val RUNS = 5
    }
}
