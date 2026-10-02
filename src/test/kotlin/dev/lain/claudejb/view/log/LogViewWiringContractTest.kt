package dev.lain.claudejb.view.log

import dev.lain.claudejb.SourceLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LogViewWiringContractTest {

    @Test
    fun `the page has exactly one emitter of the log payload`() {
        val emitters = kotlinFiles()
            .filter { LOG_PUSH.containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()

        assertEquals(listOf("LogFeed.kt"), emitters) { "the page's log push is emitted from more than one place: $emitters" }
    }

    @Test
    fun `the log payload is built off the EDT and drawn on it`() {
        val feed = source("view/log/LogFeed.kt").readText()

        assertTrue(feed.contains("executeOnPooledThread")) {
            "LogFeed reads the ring and builds the report on whatever thread asked; that is the EDT when the page asks."
        }
        assertTrue(BACK_ON_EDT.containsMatchIn(feed)) { "LogFeed does not come back to the EDT to draw." }
        assertTrue(feed.contains("copyToClient(")) { "the Copy button hands its report to no clipboard" }
        assertTrue(source("ClientClipboard.kt").readText().contains("CopyPasteManager")) {
            "the client's clipboard no longer reaches CopyPasteManager, so the Copy button reaches no clipboard"
        }
    }

    @Test
    fun `the three log messages are parsed and all three are dispatched`() {
        val bridge = source("model/bridge/JcefBridge.kt").readText()
        val handler = source("controller/bridge/BridgeLog.kt").readText()

        listOf("\"logLines\"", "\"logDebug\"", "\"logCopy\"").forEach {
            assertTrue(bridge.contains(it)) { "JcefBridge does not parse $it" }
        }
        listOf("Msg.LogLines", "Msg.LogDebug", "Msg.LogCopy").forEach {
            assertTrue(handler.contains(it)) { "nothing answers $it from the Log view" }
        }
    }

    @Test
    fun `the page's own console reaches the log the view reads`() {
        val relays = kotlinFiles().filter { it.readText().contains("override fun onConsoleMessage(") }
        val host = source("view/jcef/JcefHost.kt").readText()
        assertTrue(relays.any { it.nameWithoutExtension == "JcefHost" || host.contains("${it.nameWithoutExtension}(") }) {
            "a CSP rejection or a script error in the page never reaches the log, so the Log view cannot show it: " +
                "JcefHost installs none of the console handlers $relays"
        }
    }

    @Test
    fun `the modules and the stylesheet are declared, or the page silently does not serve them`() {
        val assembly = source("view/jcef/PageAssembly.kt").readText()

        listOf("models/log/state.js", "views/log/entries.js", "controllers/log/log.js").forEach {
            assertTrue(assembly.contains("\"$it\"")) { "$it is not in PageAssembly.appNames, so it is not served" }
            assertTrue(File(tsRoot(), it.replace(".js", ".ts")).isFile) { "$it has no source" }
        }
        assertTrue(assembly.contains("\"views/log/view.css\"")) { "views/log/view.css is not in PageAssembly.CSS_PARTS" }
        assertTrue(File(jcefRoot(), "css/views/log/view.css").isFile)
        assertTrue(File(tsRoot(), "models/panel/state.ts").readText().contains("log: {")) {
            "the dashboard has no log view for the button to open"
        }
    }

    private fun kotlinFiles(): List<File> = SourceLayout.kotlinFiles()

    private fun source(relative: String): File = SourceLayout.source(relative)

    private fun jcefRoot(): File = SourceLayout.mainDir("resources/jcef")

    private fun tsRoot(): File = SourceLayout.mainDir("ts/jcef")

    private companion object {
        val LOG_PUSH = Regex("""window\.cc\.log\(|\b(?:exec|execBuilt|PagePush)\(\s*"log"""")
        val BACK_ON_EDT = Regex("""\bedt\(\w+\.project\)""")
    }
}
