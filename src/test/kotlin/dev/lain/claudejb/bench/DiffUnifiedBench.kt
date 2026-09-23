package dev.lain.claudejb.bench

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.model.diff.DiffPresenter
import kotlin.random.Random

class DiffUnifiedBench : BasePlatformTestCase() {

    fun `test a unified diff of a one megabyte file with one line in a hundred changed`() {
        val (current, proposed) = pair()
        val median = BenchClock.medianMillis(WARMUPS, RUNS) {
            assertTrue(DiffPresenter.unifiedDiff(current, proposed).isNotEmpty())
        }
        BenchReport.record("diff_1mb", median, "ms")
    }

    private fun pair(): Pair<String, String> {
        val random = Random(SEED)
        val current = ArrayList<String>()
        var size = 0
        while (size < TARGET_CHARS) {
            val line = "    val value${current.size} = compute(${random.nextInt(BOUND)}, \"${BenchText.word(random)}\")"
            current += line
            size += line.length + 1
        }
        val proposed =
            current.map { line -> if (random.nextInt(CHANGE_ONE_IN) == 0) "$line.also { log(it) }" else line }
        return current.joinToString("\n") to proposed.joinToString("\n")
    }

    private companion object {
        const val TARGET_CHARS = 1_000_000
        const val CHANGE_ONE_IN = 100
        const val BOUND = 10_000
        const val SEED = 1
        const val WARMUPS = 3
        const val RUNS = 10
    }
}
