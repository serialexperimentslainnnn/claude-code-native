package dev.lain.claudejb.model.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextWindowTest {

    private val file = (1..1_000).joinToString("\n") { "line number $it of a long file" }

    @Test
    fun `without pressure the window is offset and limit, as before`() {
        val slice = TextWindow.slice("a\nb\nc\nd", 2, 2, 10_000)
        assertEquals(4, slice.lines)
        assertEquals(2, slice.from)
        assertEquals(3, slice.to)
        assertEquals("b\nc", slice.text)
    }

    @Test
    fun `a share of the budget stops at a whole line and says where`() {
        val slice = TextWindow.slice(file, 1, 2_000, 1_000)
        assertTrue(slice.to in 2 until 1_000) { "to ${slice.to}" }
        assertTrue(slice.text.length <= 1_000) { "${slice.text.length} chars" }
        assertEquals("line number ${slice.to} of a long file", slice.text.lines().last())
        val next = TextWindow.slice(file, slice.to + 1, 2_000, 1_000)
        assertEquals("line number ${slice.to + 1} of a long file", next.text.lines().first())
    }

    @Test
    fun `three files in one batch all get lines instead of the first eating the budget`() {
        val share = OutputBudget.DEFAULT_MAX_CHARS / 3
        val slices = List(3) { TextWindow.slice(file, 1, 400, share) }
        assertTrue(slices.all { it.to > 1 })
        assertTrue(slices.sumOf { it.text.length } < OutputBudget.DEFAULT_MAX_CHARS)
    }

    @Test
    fun `the first line always comes back, clipped at the line ceiling`() {
        val slice = TextWindow.slice("x".repeat(10_000) + "\nnext", 1, 10, 50)
        assertEquals(1, slice.to)
        assertEquals(TextWindow.MAX_LINE + 1, slice.text.length)
        assertTrue(slice.text.endsWith(Clip.ELLIPSIS))
    }

    @Test
    fun `line breaks of every kind count once and past the end is empty`() {
        assertEquals(3, TextWindow.lineCount("a\r\nb\rc"))
        assertEquals("b", TextWindow.slice("a\r\nb\rc", 2, 1, 100).text)
        val past = TextWindow.slice("a\nb", 9, 5, 100)
        assertEquals(3, past.from)
        assertEquals(2, past.to)
        assertEquals("", past.text)
    }
}
