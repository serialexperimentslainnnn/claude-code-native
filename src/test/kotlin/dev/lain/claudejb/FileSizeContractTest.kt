package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileSizeContractTest {

    @Test
    fun `the scan sees the whole tree`() {
        assertTrue(SourceLayout.kotlinFiles().size >= MIN_SOURCES) { "only ${SourceLayout.kotlinFiles().size} sources found" }
    }

    @Test
    fun `every main source stays under the ceiling`() {
        val offenders = SourceLayout.kotlinFiles()
            .map { file -> file to file.readLines().count { !it.startsWith("import ") } }
            .filter { (_, lines) -> lines > CEILING }
            .map { (file, lines) -> "${file.invariantSeparatorsPath}: $lines" }
        assertEquals(emptyList<String>(), offenders) {
            "A file over $CEILING lines, imports aside, has more than one responsibility. Split it along its seam " +
                "and move the contract tests that scan it in the same commit."
        }
    }

    private companion object {
        const val CEILING = 250
        const val MIN_SOURCES = 100
    }
}
