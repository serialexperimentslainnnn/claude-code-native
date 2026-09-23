package dev.lain.claudejb.controller.mcp.tools.run

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunIdentityTest {

    private val identity = RunIdentity<String, Any> { it == "run-2" }

    @Test
    fun `the previous instance a restart kills does not complete the new run`() {
        val previous = Any()
        val ours = Any()
        assertFalse(identity.started("run-1", previous))
        assertTrue(identity.started("run-2", ours))
        assertFalse(identity.terminated(previous))
        assertTrue(identity.terminated(ours))
    }

    @Test
    fun `only the first process of the run is followed`() {
        val first = Any()
        assertTrue(identity.started("run-2", first))
        assertFalse(identity.started("run-2", Any()))
        assertTrue(identity.terminated(first))
    }

    @Test
    fun `a refusal counts only for this run`() {
        assertTrue(identity.ours("run-2"))
        assertFalse(identity.ours("run-1"))
    }
}
