package dev.lain.claudejb.bench

object BenchClock {

    fun millis(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / NANOS_PER_MILLI
    }

    fun medianMillis(warmups: Int, runs: Int, block: () -> Unit): Double {
        repeat(warmups) { block() }
        return median(List(runs) { millis(block) })
    }

    fun median(samples: List<Double>): Double {
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private const val NANOS_PER_MILLI = 1_000_000.0
}
