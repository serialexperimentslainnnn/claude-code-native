package dev.lain.claudejb.controller.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerationSlotTest {

    private val slot = GenerationSlot<String>()

    @Test
    fun `a stale spawn never overwrites the newer process`() {
        val stale = slot.supersede()
        val fresh = slot.supersede()
        assertTrue(slot.publish(fresh, "proc2"))
        assertFalse(slot.publish(stale, "proc1"))
        assertEquals("proc2", slot.current)
    }

    @Test
    fun `a spawn superseded before publishing is refused`() {
        val gen = slot.supersede()
        slot.supersede()
        var published = false
        assertFalse(slot.publish(gen, "proc") { published = true })
        assertFalse(published)
        assertNull(slot.current)
    }

    @Test
    fun `take hands the process over once`() {
        val gen = slot.supersede()
        assertTrue(slot.publish(gen, "proc"))
        assertEquals("proc", slot.take())
        assertNull(slot.take())
    }
}
