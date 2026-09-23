package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CoroutineContainmentContractTest {

    private val sources: List<File> = SourceLayout.kotlinFiles()

    @Test
    fun `the scan sees the sources and the coroutines it allows`() {
        assertTrue(sources.size >= MIN_SOURCES) { "only ${sources.size} sources found" }
        assertTrue(sources.filter { allowed(it) }.any { file -> hits(file).isNotEmpty() }) {
            "no coroutine found even in the MCP packages; the pattern has stopped matching"
        }
    }

    @Test
    fun `coroutines live only in the MCP code and the RPC seam`() {
        val offenders = sources.filterNot { allowed(it) }.flatMap { file ->
            hits(file).map { (index, line) -> "${SourceLayout.pathInRoot(file)}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList<String>(), offenders) {
            "Coroutines belong to model/mcp, controller/mcp and the rpc packages, the platform's own RPC seam. " +
                "The rest of the plugin keeps AppExecutorUtil, ReadAction.compute, WriteCommandAction and the single edt {}."
        }
    }

    private fun hits(file: File): List<IndexedValue<String>> =
        MainSources.codeOf(file).withIndex().filter { (_, line) -> COROUTINE.containsMatchIn(line) }

    private fun allowed(file: File): Boolean {
        val pkg = "${SourceLayout.packagePath(file)}/"
        return ALLOWED_PACKAGES.any { pkg.startsWith("$it/") } || SourceLayout.pathInRoot(file) in ALLOWED_FILES
    }

    private companion object {
        const val MIN_SOURCES = 100

        val ALLOWED_PACKAGES = listOf("model/mcp", "controller/mcp", "rpc", "frontend/rpc")

        val ALLOWED_FILES = setOf("dev/lain/claudejb/controller/github/GitHubGateway.kt")

        val COROUTINE = Regex("""\bkotlinx\.coroutines\b|\bsuspend\s+fun\b|:\s*suspend\b|\brunBlocking\b""")
    }
}
