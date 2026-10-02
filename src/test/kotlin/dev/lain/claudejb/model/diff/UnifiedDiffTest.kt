package dev.lain.claudejb.model.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnifiedDiffTest {

    private val ten = (1..10).map { "l$it" }

    @Test
    fun `a hunk header counts its context lines on both sides`() {
        val proposed = ten.toMutableList().apply { this[4] = "L5" }

        val diff = UnifiedDiff.format(ten, proposed, listOf(Hunk(4, 5, 4, 5)), context = 3)

        assertEquals(
            listOf("@@ -2,7 +2,7 @@", " l2", " l3", " l4", "-l5", "+L5", " l6", " l7", " l8"),
            diff.lines(),
        )
    }

    @Test
    fun `changes whose context overlaps share one hunk and repeat no line`() {
        val proposed = ten.toMutableList().apply {
            this[2] = "L3"
            this[6] = "L7"
        }

        val diff = UnifiedDiff.format(ten, proposed, listOf(Hunk(2, 3, 2, 3), Hunk(6, 7, 6, 7)), context = 3)

        assertEquals(
            listOf(
                "@@ -1,10 +1,10 @@", " l1", " l2", "-l3", "+L3", " l4", " l5", " l6", "-l7", "+L7", " l8", " l9", " l10",
            ),
            diff.lines(),
        )
    }

    @Test
    fun `changes far apart get a hunk each`() {
        val lines = (1..20).map { "l$it" }
        val proposed = lines.toMutableList().apply {
            this[0] = "L1"
            this[19] = "L20"
        }

        val diff = UnifiedDiff.format(lines, proposed, listOf(Hunk(0, 1, 0, 1), Hunk(19, 20, 19, 20)), context = 2)

        assertEquals(
            listOf("@@ -1,3 +1,3 @@", "-l1", "+L1", " l2", " l3", "@@ -18,3 +18,3 @@", " l18", " l19", "-l20", "+L20"),
            diff.lines(),
        )
    }

    @Test
    fun `a pure insertion counts only context on the old side`() {
        val proposed = ten.toMutableList().apply { add(5, "new") }

        val diff = UnifiedDiff.format(ten, proposed, listOf(Hunk(5, 5, 5, 6)), context = 1)

        assertEquals(listOf("@@ -5,2 +5,3 @@", " l5", "+new", " l6"), diff.lines())
    }

    @Test
    fun `an insertion into an empty file starts at line zero`() {
        val diff = UnifiedDiff.format(emptyList(), listOf("a", "b"), listOf(Hunk(0, 0, 0, 2)), context = 3)

        assertEquals(listOf("@@ -0,0 +1,2 @@", "+a", "+b"), diff.lines())
    }
}
