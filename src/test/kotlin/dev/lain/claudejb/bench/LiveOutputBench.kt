package dev.lain.claudejb.bench

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.mcp.ToolOutput
import dev.lain.claudejb.controller.session.ChatSessionManager
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.session.transcript.ToolState
import dev.lain.claudejb.model.session.transcript.TranscriptEntry
import dev.lain.claudejb.model.session.transcript.TranscriptModel

class LiveOutputBench : BasePlatformTestCase() {

    private val manager get() = ChatSessionManager.getInstance(project)

    private var updates = 0

    override fun tearDown() {
        try {
            manager.all().forEach { runCatching { manager.remove(it) } }
        } finally {
            super.tearDown()
        }
    }

    fun `test ten thousand live output lines reach a session's transcript`() {
        val session = manager.create()
        session.transcript.addListener(
            object : TranscriptModel.Listener {
                override fun onUpdated(entry: TranscriptEntry) {
                    updates++
                }
            },
        )
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        repeat(WARMUPS) { push(session, "toolu_warm_$it") }
        val samples = List(RUNS) { push(session, "toolu_bench_$it") }
        assertTrue(updates > 0)
        BenchReport.record("live_output_10k", BenchClock.median(samples), "ms")
        BenchReport.record("live_output_10k_edt_updates", updates.toLong(), "updates")
    }

    private fun push(session: ClaudeSession, toolUseId: String): Double {
        session.transcript.add(
            Speaker.TOOL,
            "Bash",
            meta = "Bash",
            toolUseId = toolUseId,
            toolState = ToolState.RUNNING,
        )
        updates = 0
        return BenchClock.millis {
            repeat(LINES) { ToolOutput.line(project, toolUseId, "line $it of the build output") }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }
    }

    private companion object {
        const val LINES = 10_000
        const val WARMUPS = 2
        const val RUNS = 5
    }
}
