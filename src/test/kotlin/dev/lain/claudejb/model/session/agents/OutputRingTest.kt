package dev.lain.claudejb.model.session.agents

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutputRingTest {

    @Test
    fun `the ring keeps the last cap characters however much is appended`() {
        val ring = OutputRing(cap = 10, initial = "0123456789abc")
        assertEquals("3456789abc", ring.text())

        repeat(100) { ring.append("x$it,") }

        assertEquals(10, ring.text().length)
        assertTrue(ring.text().endsWith("x99,"))
    }

    @Test
    fun `the version moves only when something was appended`() {
        val ring = OutputRing(cap = 10)
        assertFalse(ring.append(""))
        assertEquals(0, ring.version)

        assertTrue(ring.append("a"))
        assertEquals(1, ring.version)
    }

    @Test
    fun `reading twice without an append returns the same string`() {
        val ring = OutputRing(cap = 10, initial = "abc")
        assertSame(ring.text(), ring.text())
    }
}
