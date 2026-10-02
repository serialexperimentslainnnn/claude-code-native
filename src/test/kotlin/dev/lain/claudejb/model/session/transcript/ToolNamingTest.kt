package dev.lain.claudejb.model.session.transcript

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolNamingTest {

    @Test
    fun `our own tools never trigger a project-wide refresh, whatever their name says`() {
        listOf("mcp__code__run", "mcp__run__run", "mcp__vcs__tools", "mcp__ops__domains").forEach { name ->
            assertFalse(ToolNaming.mayHaveWrittenUnknownFiles(name)) { "$name must not refresh the whole project" }
        }
    }

    @Test
    fun `Bash still refreshes the project tree`() {
        assertTrue(ToolNaming.mayHaveWrittenUnknownFiles("Bash"))
    }

    @Test
    fun `built-in file tools and readers do not refresh the tree`() {
        listOf("Read", "Edit", "Write", "Grep", "Glob", "WebFetch", null, "").forEach { name ->
            assertFalse(ToolNaming.mayHaveWrittenUnknownFiles(name)) { "$name must not refresh the whole project" }
        }
    }

    @Test
    fun `a third-party tool is judged by its name`() {
        assertTrue(ToolNaming.mayHaveWrittenUnknownFiles("mcp__other__write_file"))
        assertFalse(ToolNaming.mayHaveWrittenUnknownFiles("mcp__other__query_docs"))
    }
}
