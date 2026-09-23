package dev.lain.claudejb.view.window

import dev.lain.claudejb.MainSources
import dev.lain.claudejb.SourceLayout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolWindowWiringContractTest {

    @Test
    fun `the strip is held by the factory, never resolved by which content is selected`() {
        val code = codeOf(FACTORY)

        assertTrue(code.none { it.contains("selectedContent") }) {
            "ClaudeToolWindowFactory is back to reading the SELECTED content. There is one content and it is the strip, " +
                "so a manager with nothing selected answers null — and every caller ends in a `?.` that fails silently."
        }
        assertTrue(code.any { it.contains("createContent(tabs") }) {
            "the factory no longer makes the strip its one content:\n" + code.joinToString("\n")
        }
    }

    @Test
    fun `the tab commands exist before anything is restored`() {
        val code = codeOf(REGISTRY)
        val published = code.indexOfFirst { it.contains("val commands = TabSessionCommands(") }
        val restored = code.indexOfFirst { it.contains("restoreOrCreate()") }

        assertTrue(published >= 0 && restored >= 0) {
            "ChatRegistry no longer owns the commands or no longer restores — this contract needs rewriting, not " +
                "deleting (published at $published, restored at $restored)."
        }
        assertTrue(published < restored) {
            "The commands are created AFTER the restore. For as long as that runs, a *New chat* pressed during it and " +
                "the replacement chat that closing the last one owes the user go nowhere."
        }
    }

    @Test
    fun `a new chat is shown only once its page can draw`() {
        val body = bodyOf(codeOf(STRIP), "private fun opened(")
        val selecting = body.singleOrNull { it.trim().startsWith("select ->") }

        assertTrue(selecting != null) { "ChatTabsPanel.opened no longer has a `select ->` branch:\n" + body.joinToString("\n") }
        val deferred = selecting.orEmpty().indexOf("whenReady")
        assertTrue(deferred >= 0 && deferred < selecting.orEmpty().indexOf("show(")) {
            "opened selects the new tab without waiting for its page. The browser is empty at that moment, so the user " +
                "watches the whole UI build itself on screen — which is what gets reported as \"opening a chat reloads " +
                "the plugin\": `$selecting`"
        }
    }

    @Test
    fun `the wait for the page is bounded, and the deadline runs the block`() {
        val code = codeOf(HOST)
        val signature = code.firstOrNull { it.contains("fun whenWebReady(") }

        assertTrue(signature != null) { "JcefHost no longer offers whenWebReady — see ChatTabsPanel.opened" }
        assertTrue(signature.orEmpty().contains("WEB_READY_TIMEOUT_MS")) {
            "whenWebReady no longer defaults to a named ceiling: `$signature`. Without one, a page that never " +
                "announces leaves everything it deferred unrun, for good."
        }
        val fires = bodyOf(code, "private fun runDeferred(")
        assertTrue(fires.any { it.contains("entry.block()") }) {
            "the deadline no longer runs what it was given, so an unannounced page silently swallows the " +
                "gesture instead of serving it late:\n" + fires.joinToString("\n")
        }
        assertTrue(fires.any { it.contains("deferred.remove(entry)") }) {
            "nothing takes the entry out of the queue before running it, which is what bounds a block to one " +
                "run when the deadline and the page's own announcement race."
        }
    }

    @Test
    fun `only one place builds a chat presenter, so no two tabs can hold one session`() {
        val built = SourceLayout.kotlinFiles()
            .flatMap { file -> MainSources.codeOf(file).map { file.name to it } }
            .filter { (_, line) -> line.contains("ChatPresenter(") && !line.contains("class ChatPresenter(") }

        assertTrue(built.size == 1) {
            "A chat presenter is constructed in ${built.size} places: ${built.map { it.first }}. Exactly one is " +
                "the contract. A second presenter over a session that already has a tab puts two tabs on one `claude` " +
                "process, and closing either one disposes the session under the other."
        }
        assertTrue(built.single().first == REGISTRY) {
            "The one chat presenter is now built in ${built.single().first} rather than in ChatRegistry.open, which is " +
                "where it joins the deck in the same breath. Whatever builds it owns the one-tab-per-session invariant."
        }
    }

    @Test
    fun `the strip has no second view of a chat, and closing a chat has nothing to ask`() {
        val strip = codeOf(STRIP)
        val revived = strip.filter { it.contains("isPinnedView") || it.contains("pinnedAgent") || it.contains("fun pin(") }
        assertTrue(revived.isEmpty()) {
            "ChatTabsPanel can hold a second view of a chat again:\n" + revived.joinToString("\n") +
                "\nA pinned view is a second tab over one session, which is the state an unconditional close is safe " +
                "only in the absence of."
        }

        val removal = bodyOf(codeOf(REGISTRY), "fun close(").filter { it.contains(".remove(presenter.session)") }
        assertTrue(removal.size == 1) { "ChatRegistry.close no longer removes the session exactly once: $removal" }
        assertTrue(!removal.single().contains("if ")) {
            "closing a chat is conditional again: `${removal.single()}`. It disposes a `claude` process, and the only " +
                "reason it ever needed a condition was a tab that did not own its session."
        }
    }

    private fun bodyOf(code: List<String>, declaration: String): List<String> {
        val start = code.indexOfFirst { it.contains(declaration) }
        assertTrue(start >= 0) { "no `$declaration` in the sources — this contract needs rewriting, not deleting" }
        val rest = code.drop(start + 1)
        val end = rest.indexOfFirst { it.trim() == "}" }
        return listOf(code[start]) + if (end >= 0) rest.take(end) else rest
    }

    private fun codeOf(name: String): List<String> = MainSources.codeOf(SourceLayout.source(name))

    private companion object {
        const val FACTORY = "ClaudeToolWindowFactory.kt"
        const val STRIP = "ChatTabsPanel.kt"
        const val HOST = "JcefHost.kt"
        const val REGISTRY = "ChatRegistry.kt"
    }
}
