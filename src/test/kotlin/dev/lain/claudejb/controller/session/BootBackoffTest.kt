package dev.lain.claudejb.controller.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootBackoffTest {

    private var now = 1_000_000L
    private val backoff = BootBackoff { now }

    private fun crashAfter(millis: Long): Boolean {
        backoff.launched()
        now += millis
        return backoff.exited()
    }

    @Test
    fun `a CLI that keeps dying at startup is retried with a growing delay, then left alone`() {
        assertFalse(crashAfter(1_000))
        assertFalse(backoff.mayStart())
        now += BootBackoff.BASE_DELAY_MS
        assertTrue(backoff.mayStart())

        assertFalse(crashAfter(1_000))
        now += BootBackoff.BASE_DELAY_MS
        assertFalse(backoff.mayStart(), "the second delay is twice the first")
        now += BootBackoff.BASE_DELAY_MS
        assertTrue(backoff.mayStart())

        assertTrue(crashAfter(1_000), "the third quick exit gives up and says so once")
        assertTrue(backoff.stopped)
        now += 10 * BootBackoff.BASE_DELAY_MS
        assertFalse(backoff.mayStart())
        assertFalse(backoff.failed(), "giving up is announced only once")
    }

    @Test
    fun `the user retrying clears the failure state`() {
        repeat(BootBackoff.MAX_FAILURES) { crashAfter(1_000) }
        assertTrue(backoff.stopped)

        backoff.forgive()

        assertFalse(backoff.stopped)
        assertTrue(backoff.mayStart())
    }

    @Test
    fun `a session that ran for a while and then ended does not count as a crash`() {
        crashAfter(1_000)
        now += BootBackoff.BASE_DELAY_MS

        assertFalse(crashAfter(BootBackoff.QUICK_EXIT_MS))

        assertTrue(backoff.mayStart())
        assertFalse(crashAfter(1_000))
        now += BootBackoff.BASE_DELAY_MS
        assertTrue(backoff.mayStart(), "the count started over after the long run")
    }

    @Test
    fun `a stop the plugin asked for is not a crash`() {
        backoff.launched()
        backoff.stoppedOnPurpose()

        assertFalse(backoff.exited())
        assertTrue(backoff.mayStart())
    }

    @Test
    fun `nothing launched means nothing exited`() {
        assertFalse(backoff.exited())
        assertTrue(backoff.mayStart())
        assertEquals(false, backoff.stopped)
    }
}
