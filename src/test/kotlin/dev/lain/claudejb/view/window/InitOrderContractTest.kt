package dev.lain.claudejb.view.window

import dev.lain.claudejb.SourceLayout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InitOrderContractTest {

    private val classBodyInit = Regex("""^ {4}init \{""")
    private val classBodyProperty = Regex("""^ {4}(?:private |internal |protected )?(?:val|var) """)

    @Test
    fun `no class-body property is declared after the init block that could use it`() {
        val offenders = mutableListOf<String>()
        assertTrue(SourceLayout.kotlinFiles().size >= MIN_SOURCES) { "the scan sees no source tree; it would pass vacuously" }

        SourceLayout.kotlinFiles().forEach { file ->
            val lines = file.readLines()
            val initAt = lines.indexOfFirst { classBodyInit.containsMatchIn(it) }
            if (initAt < 0) return@forEach
            lines.drop(initAt + 1).forEachIndexed { offset, line ->
                if (classBodyProperty.containsMatchIn(line)) {
                    offenders += "${file.name}:${initAt + offset + 2}: ${line.trim()}"
                }
            }
        }

        assertTrue(offenders.isEmpty()) {
            "These properties are declared AFTER their class's init block, so they are still null/0 while it " +
                "runs. Move them above `init`.\n" + offenders.joinToString("\n")
        }
    }

    private companion object {
        const val MIN_SOURCES = 100
    }
}
