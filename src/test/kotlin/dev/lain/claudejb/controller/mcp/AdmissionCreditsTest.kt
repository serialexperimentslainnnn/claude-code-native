package dev.lain.claudejb.controller.mcp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdmissionCreditsTest {

    private var now = 1_000L
    private val credits = AdmissionCredits(ttlMillis = 100) { now }

    @Test
    fun `no grant admits nobody`() {
        assertFalse(credits.consume())
    }

    @Test
    fun `a grant admits exactly its count`() {
        credits.grant(2)
        assertTrue(credits.consume())
        assertTrue(credits.consume())
        assertFalse(credits.consume())
    }

    @Test
    fun `a withdrawn grant leaves nothing for a later client`() {
        val grant = credits.grant(3)
        assertTrue(credits.consume())
        grant.withdraw()
        assertFalse(credits.consume())
    }

    @Test
    fun `withdrawing one grant keeps another`() {
        val dead = credits.grant(1)
        credits.grant(1)
        dead.withdraw()
        assertTrue(credits.consume())
        assertFalse(credits.consume())
    }

    @Test
    fun `an unused grant expires`() {
        credits.grant(1)
        now += 101
        assertFalse(credits.consume())
    }

    @Test
    fun `a zero grant admits nobody and withdraws cleanly`() {
        credits.grant(0).withdraw()
        assertFalse(credits.consume())
    }
}
