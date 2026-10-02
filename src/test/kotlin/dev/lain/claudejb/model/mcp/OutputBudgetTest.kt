package dev.lain.claudejb.model.mcp

import dev.lain.claudejb.model.mcp.toon.Toon
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutputBudgetTest {

    @Test
    fun `a cut never falls inside a surrogate pair, so the reply still encodes`() {
        val budget = OutputBudget(200)
        for (shift in 0..3) {
            val text = "x".repeat(shift) + "😀".repeat(200)
            val fitted = budget.fit(text)
            Toon.encode(JsonPrimitive(fitted))
            assertTrue(fitted.length <= 200) { fitted }
        }
    }

    @Test
    fun `a clipped line keeps whole code points and ends with an ellipsis`() {
        val clipped = Clip.line("a" + "😀".repeat(10), 4)
        assertEquals("a😀" + Clip.ELLIPSIS, clipped)
        assertEquals("short", Clip.line("short", 10))
    }
}
