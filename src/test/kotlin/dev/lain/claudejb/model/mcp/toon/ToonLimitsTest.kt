package dev.lain.claudejb.model.mcp.toon

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ToonLimitsTest {

    @Test
    fun `nesting deeper than the limit is a typed error, not a stack overflow`() {
        val deep = (0 until 20_000).joinToString("\n") { "  ".repeat(it) + "k$it:" }
        assertThrows<ToonException> { Toon.decode(deep) }
        val fields = "rows[1]{" + "a{".repeat(5_000) + "b" + "}".repeat(5_000) + "}:\n  1"
        assertThrows<ToonException> { Toon.decode(fields) }
    }

    @Test
    fun `nesting within the limit still decodes`() {
        val nested = (0 until ToonLines.MAX_DEPTH).joinToString("\n") { "  ".repeat(it) + "k$it:" } + " 1"
        var node = Toon.decode(nested).jsonObject
        repeat(ToonLines.MAX_DEPTH - 1) { node = node.getValue("k$it").jsonObject }
        assertEquals("1", node.getValue("k${ToonLines.MAX_DEPTH - 1}").jsonPrimitive.content)
    }

    @Test
    fun `a number beyond any sane range is a typed error`() {
        assertThrows<ToonException> { Toon.decode("id: 1e99999999999") }
        assertThrows<ToonException> { Toon.decode("id: 1e999999999") }
        assertThrows<ToonException> { Toon.decode("id: " + "9".repeat(1_000)) }
    }

    @Test
    fun `ordinary numbers keep their canonical form`() {
        val decoded = Toon.decode("a: 12\nb: -0.5\nc: 1e300").jsonObject
        assertEquals("12", decoded.getValue("a").jsonPrimitive.content)
        assertEquals("-0.5", decoded.getValue("b").jsonPrimitive.content)
        assertEquals("1e+300", decoded.getValue("c").jsonPrimitive.content)
    }
}
