package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.SourceLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PageStateRecoveryContractTest {

    @Test
    fun `a load that did not deliver the page must not drain the queued pushes`() {
        val body = bodyOf(source("view/jcef/PageLoadHandler.kt").readLines(), "override fun onLoadEnd(")
        val guard = body.indexOfFirst { it.contains("pageArrived(") }
        val drain = body.indexOfFirst { it.contains("onArrived()") }

        assertTrue(guard >= 0) {
            "onLoadEnd no longer asks pageArrived() whether the page actually arrived. Without that question " +
                "a failed load drains the queue into a page that cannot run it, and the next rung of the " +
                "PageRoute ladder comes up with nothing to draw."
        }
        assertTrue(drain > guard) {
            "onLoadEnd reports the page as arrived before deciding whether it did."
        }
        assertTrue(body.subList(guard, drain).any { it.trim() == "return" }) {
            "the pageArrived() check must RETURN when the page did not arrive — logging it and carrying on " +
                "spends the queue exactly as before."
        }
    }

    @Test
    fun `the failure verdict is recorded, cleared and actually consulted`() {
        val text = source("view/jcef/PageLoadHandler.kt").readText()
        listOf(
            "override fun onLoadError(" to
                "nothing records that a load failed, so an unreachable route reads as a delivered page",
            "override fun onLoadStart(" to
                "nothing clears the failure verdict, so one failed load would condemn every load after it",
            "pageArrived(httpStatusCode, mainFrameLoadFailed)" to
                "onLoadEnd no longer consults the failure verdict it was given",
        ).forEach { (needle, why) ->
            assertTrue(text.contains(needle)) { "$why (looked for `$needle`)" }
        }
    }

    @Test
    fun `the ready watchdog is armed when the browser starts loading, not when the page is handed over`() {
        val deliver = bodyOf(source("view/jcef/PageDelivery.kt").readLines(), "private fun deliver(")
        assertTrue(deliver.none { ARMS.containsMatchIn(it) }) {
            "deliver() arms the ready watchdog before the browser exists. JBCefOsrComponent.addNotify creates the " +
                "browser, and a chat opened into a component not yet on screen — the replacement for a closed " +
                "last tab — spends the whole grace period before its first navigation can start, then falls off " +
                "the ladder with the page never run.\n" + deliver.joinToString("\n")
        }
        val start = bodyOf(source("view/jcef/PageLoadHandler.kt").readLines(), "override fun onLoadStart(")
        assertTrue(start.any { it.contains("onStarted()") }) {
            "onLoadStart no longer reports that the browser began loading.\n" + start.joinToString("\n")
        }
        assertTrue(source("view/jcef/JcefHost.kt").readText().contains("onStarted = { delivery?.pageLoadStarted() }")) {
            "the host no longer arms the delivery's watchdog from the load start, so a rung that hangs is never left"
        }
    }

    @Test
    fun `a disposed host runs nothing in its browser`() {
        val exec = bodyOf(source("view/jcef/JcefHost.kt").readLines(), "fun exec(")
        assertTrue(exec.any { it.contains("disposed") }) {
            "exec no longer checks disposed. A pooled payload that finishes after the tab closed then executes " +
                "JavaScript in a browser that is being torn down.\n" + exec.joinToString("\n")
        }
    }

    @Test
    fun `the Ready message re-pushes the tab bar, like everything else the page owes`() {
        val lines = source("controller/bridge/BridgeLifecycle.kt").readLines()
        val start = lines.indexOfFirst { it.contains("Msg.Ready ->") }
        assertTrue(start >= 0) { "BridgeLifecycle no longer handles Msg.Ready" }
        val branch = if (lines[start].trimEnd().endsWith("{")) {
            val length = lines.drop(start + 1).indexOfFirst { it == "            }" }
            assertTrue(length >= 0) { "could not find the end of the Msg.Ready branch" }
            lines.subList(start, start + 1 + length)
        } else {
            listOf(lines[start])
        }
        val delegate = DELEGATE.find(branch.joinToString("\n"))?.groupValues?.get(1)
        val reached = branch + delegate?.let { bodyOf(source("view/window/ChatPresenter.kt").readLines(), "fun $it(") }.orEmpty()

        assertTrue(reached.any { it.contains("agentTabs.render()") }) {
            "the Ready branch re-pushes everything the page needs EXCEPT the tab bar. A page that reloaded, " +
                "or that came up on a later rung of the delivery ladder, is then drawn with an empty chat " +
                "list — and the page hides #tabsbar entirely, dashboard view buttons included.\n" +
                reached.joinToString("\n")
        }
    }

    @Test
    fun `the tab bar has exactly one emitter, and it is the one Ready calls`() {
        val emitters = SourceLayout.kotlinFiles()
            .filter { TABS_PUSH.containsMatchIn(it.readText()) }
            .map { it.name }
        assertEquals(listOf("ChatAgentTabs.kt"), emitters) {
            "the page's tabs push is emitted from more than one place: $emitters"
        }
    }

    private fun bodyOf(lines: List<String>, signature: String): List<String> {
        val from = lines.indexOfFirst { it.trimStart().startsWith(signature) }
        assertTrue(from >= 0) { "no `$signature` declared" }
        val indent = lines[from].takeWhile { it == ' ' }
        val length = lines.drop(from + 1).indexOfFirst { it == "$indent}" }
        assertTrue(length >= 0) { "`$signature` has no closing brace at its own indent" }
        return lines.subList(from, from + 2 + length)
    }

    private fun source(relative: String): File = SourceLayout.source(relative)

    private companion object {
        val ARMS = Regex("""\barm\(""")
        val DELEGATE = Regex("""\bpresenter\.(\w+)\(\)""")
        val TABS_PUSH = Regex("""window\.cc\.tabs\(|\b(?:exec|execBuilt|PagePush)\(\s*"tabs"""")
    }
}
