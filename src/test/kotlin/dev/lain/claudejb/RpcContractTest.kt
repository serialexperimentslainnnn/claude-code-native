package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RpcContractTest {

    private val channel: Map<String, String> = CONSTANT.findAll(SourceLayout.source("rpc/FrontendChannel.kt").readText())
        .associate { it.groupValues[1] to it.groupValues[2] }

    private val prefix: String = channel["PREFIX"] ?: error("FrontendChannel no longer declares PREFIX")

    private val pageMethods: Set<String> = SourceLayout.tsFiles().flatMap { definedIn(it.readText()) }.toSet()

    private val pushed: List<Push> = backendSources().flatMap { file ->
        val text = file.readText()
        val literals = listOf(WINDOW_CC, PUSH_LITERAL, SNAPSHOT_LITERAL).flatMap { pattern ->
            pattern.findAll(text).map { it.groupValues[1] }.toList()
        }
        val constants = PUSH_CONSTANT.findAll(text).map { channel[it.groupValues[1]] ?: "FrontendChannel.${it.groupValues[1]}" }
        (literals + constants).map { Push(file, it) }
    }

    @Test
    fun `the scan sees the page's methods and the backend's pushes`() {
        assertTrue(pageMethods.size >= MIN_PAGE_METHODS) { "only ${pageMethods.size} window.cc methods found: $pageMethods" }
        assertTrue(pushed.size >= MIN_PUSHES) { "only ${pushed.size} pushes found in the backend" }
    }

    @Test
    fun `every push the backend names is a page method or a frontend channel`() {
        val orphans = pushed
            .filterNot { it.method in pageMethods || it.method.startsWith(prefix) }
            .map { "${SourceLayout.pathInRoot(it.file)}: ${it.method}" }
            .distinct()
        assertEquals(emptyList<String>(), orphans) {
            "The backend pushes a method the page never defines, and it is not addressed to the frontend itself " +
                "($prefix…). On the page it is a silent no-op; nothing on either side reports it."
        }
    }

    private fun definedIn(ts: String): List<String> {
        val aliases = ALIAS.findAll(ts).map { Regex.escape(it.groupValues[1]) }.toList()
        val owner = (listOf("""window\.cc""") + aliases).joinToString("|")
        return Regex("""(?<![\w.$])(?:$owner)\.(\w+)\s*=(?!=)""").findAll(ts).map { it.groupValues[1] }.toList()
    }

    private fun backendSources(): List<File> {
        val elsewhere = (SourceLayout.rootsOf("frontend", "kotlin") + SourceLayout.rootsOf("shared", "kotlin"))
        return SourceLayout.kotlinFiles()
            .filter { file -> elsewhere.none { file.startsWith(it) } }
            .filterNot { "${SourceLayout.packagePath(it)}/".startsWith("frontend/") }
    }

    private class Push(val file: File, val method: String)

    private companion object {
        const val MIN_PAGE_METHODS = 30
        const val MIN_PUSHES = 20

        val CONSTANT = Regex("""const\s+val\s+(\w+)\s*=\s*"([^"]*)"""")
        val ALIAS = Regex("""(?:const|let|var)\s+(\w+)\s*=\s*\(?\s*window\.cc\b""")
        val WINDOW_CC = Regex("""\bwindow\.cc\.(\w+)""")
        val PUSH_LITERAL = Regex("""\b(?:PagePush|exec|execBuilt)\(\s*(?:method\s*=\s*)?"(\w+)"""")
        val SNAPSHOT_LITERAL = Regex("""\bsend\(\s*\w+\s*,\s*"(\w+)"""")
        val PUSH_CONSTANT = Regex("""\b(?:PagePush|exec|execBuilt)\(\s*(?:method\s*=\s*)?FrontendChannel\.(\w+)""")
    }
}
