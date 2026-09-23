package dev.lain.claudejb.view.window

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import dev.lain.claudejb.controller.commands.TabSessionCommands
import dev.lain.claudejb.controller.session.ChatSessionManager
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.controller.session.McpLink
import dev.lain.claudejb.model.session.launch.LaunchOptions
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.rpc.ChatEvent
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import dev.lain.claudejb.util.logger
import dev.lain.claudejb.view.git.ChatGitRefresh
import dev.lain.claudejb.view.payload.chat.JcefTabsData
import dev.lain.claudejb.view.payload.panel.JcefSessionData
import dev.lain.claudejb.view.window.ChatSnapshots.Kind
import java.util.UUID

@Service(Service.Level.PROJECT)
internal class ChatRegistry(private val project: Project) : Disposable {

    private val deck = ChatDeck<ChatPresenter>({ it.id }, { it.ref() })

    private var restoring = false

    private var disposed = false

    private var prewarmed = false

    val commands = TabSessionCommands(project, this)

    val git = ChatGitRefresh(project, this) { repaint(Kind.SESSION) }

    val attention = ChatAttention(project, this)

    fun all(): List<ChatPresenter> = deck.all()

    fun isEmpty(): Boolean = deck.isEmpty()

    fun selected(): ChatPresenter? = deck.selected

    fun presenter(id: ChatId): ChatPresenter? = deck.find(id)

    fun chats(): List<ChatRef> {
        if (!restoring) {
            restoring = true
            commands.restoreOrCreate()
        }
        return all().map { it.ref() }
    }

    fun subscribe(listener: (ChatEvent) -> Unit): () -> Unit = deck.subscribe(listener)

    fun open(session: ClaudeSession, select: Boolean): ChatPresenter {
        if (!prewarmed) {
            prewarmed = true
            McpLink.prewarm(project)
        }
        session.settings.adopt(LaunchOptions.from(ClaudeSettings.getInstance(project)))
        session.start()
        val presenter = ChatPresenter(project, session, ChatId(UUID.randomUUID().toString()), this)
        Disposer.register(this, presenter)
        deck.add(presenter, select)
        deck.selected?.let { ChatSessionManager.getInstance(project).setActive(it.session) }
        repaint(Kind.TABS)
        return presenter
    }

    fun newChat(): ChatPresenter = open(ChatSessionManager.getInstance(project).create(), true)

    fun selectedOrNew(): ChatPresenter = selected() ?: newChat()

    fun select(id: ChatId) {
        val presenter = deck.select(id) ?: return LOG.warn(unknown("select", id))
        onSelected(presenter)
    }

    private fun onSelected(presenter: ChatPresenter) {
        presenter.attention = false
        ChatSessionManager.getInstance(project).setActive(presenter.session)
        runCatching { presenter.transcript.showTranscript(null) }
            .onFailure { LOG.warn("Claude Code: showing '${presenter.session.title}' failed to reset its transcript", it) }
        repaint(Kind.TABS)
        presenter.frontend.focusInput()
    }

    fun reveal(presenter: ChatPresenter) {
        select(presenter.id)
        showToolWindow()
    }

    fun showToolWindow() {
        toolWindow()?.activate(null)
    }

    fun onScreen(presenter: ChatPresenter): Boolean = toolWindow()?.isVisible == true && deck.selected === presenter

    private fun toolWindow() = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)

    fun close(id: ChatId) {
        val wasSelected = deck.selected?.id == id
        val presenter = deck.remove(id) ?: return LOG.warn(unknown("close", id))
        try {
            if (wasSelected) deck.selected?.let(::onSelected)
            repaint(Kind.TABS)
            ChatSessionManager.getInstance(project).remove(presenter.session)
        } finally {
            Disposer.dispose(presenter)
            replaceLastChat()
        }
    }

    fun onTitleChanged(presenter: ChatPresenter) {
        deck.renamed(presenter)
        repaint(Kind.TABS)
    }

    fun badge(presenter: ChatPresenter) {
        if (presenter === deck.selected) return
        presenter.attention = true
        repaint(Kind.TABS)
    }

    fun chatList(): List<JcefTabsData.Chat> = all().map {
        JcefTabsData.Chat(it.id.value, tabTitle(it.session.title), it === deck.selected, it.attention)
    }

    fun workloads(): List<JcefSessionData.Workload> = all().map {
        JcefSessionData.Workload(it.id.value, tabTitle(it.session.title), it === deck.selected, it.session)
    }

    private fun repaint(kind: Kind) = all().forEach { it.snapshots.mark(kind) }

    private fun replaceLastChat() {
        if (!deck.isEmpty()) return
        ApplicationManager.getApplication().invokeLater(
            { if (!disposed && deck.isEmpty()) newChat() },
            ModalityState.defaultModalityState(),
        )
    }

    private fun unknown(gesture: String, id: ChatId): String =
        "Claude Code chats: $gesture named '${id.value}', which is not an open chat. " +
            "Open now: ${all().map { it.id.value to it.session.title }}"

    override fun dispose() {
        disposed = true
        deck.clear()
    }

    companion object {
        const val TOOL_WINDOW_ID = "Claude Code"

        private const val TAB_TITLE_MAX = 22

        private val LOG = logger<ChatRegistry>()

        @Volatile
        var vibe: Boolean = false
            private set

        fun getInstance(project: Project): ChatRegistry = project.service()

        fun everywhere(action: (ChatPresenter) -> Unit) {
            ProjectManager.getInstance().openProjects.forEach { project ->
                project.serviceIfCreated<ChatRegistry>()?.all()?.forEach(action)
            }
        }

        fun repaintEverywhere(vararg kinds: Kind) = everywhere { it.snapshots.mark(*kinds) }

        fun changeVibe(on: Boolean) {
            vibe = on
            everywhere { it.frontend.pushVibe() }
        }

        fun tabTitle(title: String): String =
            TabSessionCommands.truncate(title.trim().ifBlank { "Chat" }, TAB_TITLE_MAX)
    }
}
