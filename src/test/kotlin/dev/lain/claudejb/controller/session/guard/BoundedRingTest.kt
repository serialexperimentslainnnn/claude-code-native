package dev.lain.claudejb.controller.session.guard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundedRingTest {

    @Test
    fun `the ring keeps only the newest items`() {
        val ring = BoundedRing<Int>(3)
        (1..5).forEach(ring::add)
        assertEquals(listOf(3, 4, 5), ring.filter { true })
    }

    @Test
    fun `a restore larger than the ring keeps its tail`() {
        val ring = BoundedRing<Int>(2)
        ring.add(9)
        ring.replaceAll(listOf(1, 2, 3))
        assertEquals(listOf(2, 3), ring.filter { true })
    }

    @Test
    fun `an empty ring reports it`() {
        val ring = BoundedRing<String>(2)
        assertTrue(ring.isEmpty())
        ring.replaceAll(emptyList())
        assertTrue(ring.isEmpty())
    }
}
