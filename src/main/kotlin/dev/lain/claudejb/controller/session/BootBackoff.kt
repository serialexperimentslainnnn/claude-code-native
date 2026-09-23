package dev.lain.claudejb.controller.session

class BootBackoff(private val clock: () -> Long = System::currentTimeMillis) {

    private var launchedAt = 0L
    private var failures = 0
    private var nextAttemptAt = 0L

    @Volatile var stopped = false
        private set

    @Synchronized
    fun launched() {
        launchedAt = clock()
    }

    @Synchronized
    fun stoppedOnPurpose() {
        launchedAt = 0L
    }

    @Synchronized
    fun exited(): Boolean {
        if (launchedAt == 0L) return false
        val lived = clock() - launchedAt
        launchedAt = 0L
        if (lived >= QUICK_EXIT_MS) {
            failures = 0
            nextAttemptAt = 0L
            return false
        }
        return failed()
    }

    @Synchronized
    fun failed(): Boolean {
        launchedAt = 0L
        failures++
        if (failures >= MAX_FAILURES) {
            val first = !stopped
            stopped = true
            return first
        }
        nextAttemptAt = clock() + (BASE_DELAY_MS shl (failures - 1))
        return false
    }

    @Synchronized
    fun mayStart(): Boolean = !stopped && clock() >= nextAttemptAt

    @Synchronized
    fun forgive() {
        failures = 0
        nextAttemptAt = 0L
        stopped = false
    }

    companion object {
        const val QUICK_EXIT_MS = 30_000L
        const val BASE_DELAY_MS = 5_000L
        const val MAX_FAILURES = 3
    }
}
