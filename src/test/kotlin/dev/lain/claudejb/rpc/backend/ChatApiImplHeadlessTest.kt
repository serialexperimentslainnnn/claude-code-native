package dev.lain.claudejb.rpc.backend

import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import dev.lain.claudejb.integration.FakeClaudeTestBase
import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.view.window.ChatRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ChatApiImplHeadlessTest : FakeClaudeTestBase() {

    private val api = ChatApiImpl()

    private val registry get() = ChatRegistry.getInstance(project)

    override fun setUp() {
        super.setUp()
        manager.remove(newSessionWith(FIXTURE))
    }

    override fun tearDown() {
        try {
            registry.all().map { it.id }.forEach(registry::close)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        } finally {
            super.tearDown()
        }
    }

    fun `test a new chat is announced, selectable, pushes its page state and closes`() = runBlocking {
        val id = project.projectId()
        val events = api.events(id)
        val chat = api.newChat(id)
        val announced = withTimeout(EVENT_TIMEOUT_MS) {
            events.first { (it is ChatEvent.Opened && it.chat == chat) || (it is ChatEvent.Listed && chat in it.chats) }
        }
        assertTrue(announced is ChatEvent.Opened || announced is ChatEvent.Listed)
        assertTrue(api.chats(id).contains(chat))

        api.select(id, chat.id)
        assertSame(registry.presenter(chat.id), registry.selected())

        val pushes = api.pushes(id, chat.id)
        assertTrue(pushes.first().method.isNotBlank())

        api.post(id, chat.id, """{"type":"diag","report":"from the page"}""")
        api.close(id, chat.id)
        waitUntil("the closed chat leaves the registry") { registry.presenter(chat.id) == null }
    }

    fun `test a chat that is not open is ignored rather than failing the call`() = runBlocking {
        val id = project.projectId()
        val stranger = ChatId("not-a-chat")
        api.select(id, stranger)
        api.close(id, stranger)
        api.post(id, stranger, "{}")
        assertEquals(emptyList<Any>(), api.pushes(id, stranger).toList())
    }

    fun `test the host describes its gear menu and ignores an entry that is gone`() = runBlocking {
        val id = project.projectId()
        api.newChat(id)
        val items = HostWindowApiImpl().gear(id)
        val settings = items.single { it.text == "Settings…" }
        assertTrue(settings.enabled)
        assertTrue(items.any { it.separator })
        assertEquals(items.size, items.map { it.path }.distinct().size)
        HostWindowApiImpl().runGear(id, listOf(Int.MAX_VALUE))
        HostWindowApiImpl().runGear(id, emptyList())
    }

    private companion object {
        const val FIXTURE = "multi_message.jsonl"

        const val EVENT_TIMEOUT_MS = 10_000L
    }
}
