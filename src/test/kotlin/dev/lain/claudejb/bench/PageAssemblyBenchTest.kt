package dev.lain.claudejb.bench

import dev.lain.claudejb.frontend.jcef.PageAssembly
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("bench")
class PageAssemblyBenchTest {

    @Test
    fun `the chat page is assembled from its resources`() {
        val first = BenchClock.millis { PageAssembly.build() }
        val second = BenchClock.millis { PageAssembly.build() }
        val median = BenchClock.medianMillis(WARMUPS, RUNS) { assertTrue(PageAssembly.build().html.isNotEmpty()) }
        BenchReport.record("page_assembly_first_call", first, "ms")
        BenchReport.record("page_assembly_second_call", second, "ms")
        BenchReport.record("page_assembly", median, "ms")
    }

    private companion object {
        const val WARMUPS = 5
        const val RUNS = 20
    }
}
