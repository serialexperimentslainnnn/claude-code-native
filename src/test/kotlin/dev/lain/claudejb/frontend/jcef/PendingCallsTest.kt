package dev.lain.claudejb.frontend.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingCallsTest {

    @Test
    fun `a snapshot keeps only its latest call, placed where the latest arrived`() {
        val pending = PendingCalls()
        pending.add("session", "s1")
        pending.add(null, "d1")
        pending.add("session", "s2")

        assertEquals(listOf("d1", "s2"), pending.drain().calls)
    }

    @Test
    fun `deltas below the cap are all kept, in order`() {
        val pending = PendingCalls(maxDeltas = 3)
        listOf("a", "b", "c").forEach { pending.add(null, it) }

        val drained = pending.drain()
        assertEquals(listOf("a", "b", "c"), drained.calls)
        assertFalse(drained.overflowed)
    }

    @Test
    fun `past the cap every delta is dropped, the snapshots survive and a resync is asked for`() {
        val pending = PendingCalls(maxDeltas = 2)
        pending.add("meta", "m")
        listOf("a", "b", "c", "d").forEach { pending.add(null, it) }
        pending.add("tabs", "t")

        val drained = pending.drain()
        assertEquals(listOf("m", "t"), drained.calls)
        assertTrue(drained.overflowed)
    }

    @Test
    fun `a drain starts the queue over`() {
        val pending = PendingCalls(maxDeltas = 1)
        pending.add(null, "a")
        pending.add(null, "b")
        pending.drain()

        pending.add(null, "c")
        val drained = pending.drain()
        assertEquals(listOf("c"), drained.calls)
        assertFalse(drained.overflowed)
    }
}
