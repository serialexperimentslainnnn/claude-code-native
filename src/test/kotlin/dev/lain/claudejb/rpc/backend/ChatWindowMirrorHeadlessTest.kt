package dev.lain.claudejb.rpc.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import dev.lain.claudejb.frontend.window.ChatCard
import dev.lain.claudejb.frontend.window.ChatTabsPanel
import dev.lain.claudejb.integration.FakeClaudeTestBase
import dev.lain.claudejb.model.session.transcript.SessionRef
import dev.lain.claudejb.view.window.ChatRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.swing.JComponent
import javax.swing.JPanel

class ChatWindowMirrorHeadlessTest : FakeClaudeTestBase() {

    private class QuietCard : ChatCard {
        override val component: JComponent = JPanel()

        override fun focus() = Unit

        override fun focusTarget(): JComponent = component

        override fun whenReady(block: () -> Unit) = block()

        override fun dispose() = Unit
    }

    private val api = ChatApiImpl()

    private val registry get() = ChatRegistry.getInstance(project)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var window: ChatTabsPanel

    override fun setUp() {
        super.setUp()
        manager.remove(newSessionWith(FIXTURE))
        window = ChatTabsPanel({}) { QuietCard() }
        scope.launch {
            api.events(project.projectId()).collect { event ->
                ApplicationManager.getApplication().invokeLater { window.event(event) }
            }
        }
    }

    override fun tearDown() {
        try {
            scope.cancel()
            Disposer.dispose(window)
            registry.all().map { it.id }.forEach(registry::close)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        } finally {
            super.tearDown()
        }
    }

    fun `test the window keeps every chat and the selection of the host through opening and closing`() {
        val first = registry.newChat()
        registry.newChat()
        val third = registry.newChat()
        mirrors("three chats opened")

        registry.close(third.id)
        mirrors("the chat on screen closed")

        registry.close(first.id)
        mirrors("a chat off screen closed")

        registry.all().map { it.id }.forEach(registry::close)
        waitUntil("a fresh chat replaces the last one") { registry.all().isNotEmpty() }
        mirrors("every chat closed and replaced")
    }

    fun `test reopening a saved session keeps the window on the host's chats`() {
        val saved = manager.create()
        saved.persistence.restore(SAVED_SESSION, emptyList())
        registry.open(saved, select = false)
        registry.newChat()
        mirrors("a saved session and a new chat")

        registry.commands.reopen(SessionRef(SAVED_SESSION, "Saved", 0L))
        mirrors("the saved session reopened")
        assertEquals(SAVED_SESSION, registry.selected()?.session?.sessionId)
    }

    private fun mirrors(step: String) {
        waitUntil("the window matches the host after: $step") {
            window.chats().toSet() == registry.all().map { it.id }.toSet() && window.selected == registry.selected()?.id
        }
    }

    private companion object {
        const val FIXTURE = "multi_message.jsonl"

        const val SAVED_SESSION = "22222222-3333-4444-5555-666666666666"
    }
}
