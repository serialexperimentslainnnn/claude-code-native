package dev.lain.claudejb.view.window

import dev.lain.claudejb.integration.FakeClaudeTestBase
import dev.lain.claudejb.model.session.transcript.SessionRef

class ChatRegistryCloseHeadlessTest : FakeClaudeTestBase() {

    private val registry get() = ChatRegistry.getInstance(project)

    override fun setUp() {
        super.setUp()
        manager.remove(newSessionWith(FIXTURE))
    }

    override fun tearDown() {
        try {
            registry.all().map { it.id }.forEach(registry::close)
        } finally {
            super.tearDown()
        }
    }

    fun `test closing one of two tabs on the same saved session leaves the other open and alive`() {
        val first = restored()
        val second = restored()
        keepOnly(first, second)
        registry.close(first.id)
        waitUntil("the closed tab leaves the registry") { registry.presenter(first.id) == null }
        assertEquals(listOf(second.id), registry.all().map { it.id })
        assertTrue(manager.all().contains(second.session))
    }

    fun `test closing the last tab opens a fresh one`() {
        val only = restored()
        keepOnly(only)
        registry.close(only.id)
        waitUntil("a fresh chat replaces the last one") { registry.all().any { it.id != only.id } }
        assertEquals(1, registry.all().size)
    }

    fun `test reopening a saved session that is already open selects its tab instead of duplicating it`() {
        val opened = restored()
        registry.newChat()
        val before = registry.all().map { it.id }
        registry.commands.reopen(SessionRef(SAVED_SESSION, "Saved", 0L))
        waitUntil("the open tab is selected") { registry.selected() === opened }
        assertEquals(before, registry.all().map { it.id })
    }

    private fun restored(): ChatPresenter {
        val session = manager.create()
        session.persistence.restore(SAVED_SESSION, emptyList())
        return registry.open(session, true)
    }

    private fun keepOnly(vararg keep: ChatPresenter) {
        registry.all().filterNot { it in keep }.forEach { registry.close(it.id) }
        waitUntil("only the tabs under test are open") { registry.all().toSet() == keep.toSet() }
    }

    private companion object {
        const val FIXTURE = "multi_message.jsonl"

        const val SAVED_SESSION = "11111111-2222-3333-4444-555555555555"
    }
}
