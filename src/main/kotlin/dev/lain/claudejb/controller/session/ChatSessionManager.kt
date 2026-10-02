package dev.lain.claudejb.controller.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.lain.claudejb.controller.session.history.SessionHistory
import dev.lain.claudejb.model.session.launch.LaunchOptions
import dev.lain.claudejb.model.settings.ClaudeSettings
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class ChatSessionManager(private val project: Project) : Disposable {

    private val sessions = CopyOnWriteArrayList<ClaudeSession>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var active: ClaudeSession? = null
        private set

    fun all(): List<ClaudeSession> = sessions.toList()

    fun addListener(onSessionsChanged: () -> Unit) = listeners.add(onSessionsChanged)

    @Synchronized
    fun create(): ClaudeSession = register(ClaudeSession(project, nextChatTitle()))

    private fun nextChatTitle(): String {
        val taken = sessions.mapNotNullTo(HashSet()) { session ->
            session.title.removePrefix(CHAT_TITLE_PREFIX).takeIf { it != session.title }?.toIntOrNull()
        }
        var n = 1
        while (n in taken) n++
        return "$CHAT_TITLE_PREFIX$n"
    }

    fun gitChat(): ClaudeSession? = sessions.firstOrNull { it.gitIntegration }

    @Synchronized
    fun gitChatOrCreate(): ClaudeSession {
        gitChat()?.let { return it }
        val previous = active
        val made = register(ClaudeSession(project, GIT_CHAT_TITLE, gitIntegration = true))
        previous?.let { setActive(it) }
        return made
    }

    private fun register(session: ClaudeSession): ClaudeSession {
        sessions.add(session)
        active = session
        fireChanged()
        return session
    }

    fun setActive(session: ClaudeSession?) {
        if (session != null && session !in sessions) return
        active = session
    }

    fun activeOrCreate(): ClaudeSession = active ?: create()

    fun adoptSettings() {
        val next = LaunchOptions.from(ClaudeSettings.getInstance(project))
        sessions.forEach { it.settings.adopt(next) }
    }

    fun remove(session: ClaudeSession) {
        if (!sessions.remove(session)) return
        Disposer.dispose(session)
        if (active == session) active = sessions.lastOrNull()
        persistOpenTabs()
        fireChanged()
    }

    private fun persistOpenTabs() {
        val ids = sessions.filterNot { it.gitIntegration }.mapNotNull { it.sessionId }
        ClaudeSettings.persist { if (!project.isDisposed) SessionHistory.getInstance(project).setOpenSessions(ids) }
    }

    private fun fireChanged() = listeners.forEach { it() }

    override fun dispose() {
        sessions.forEach { Disposer.dispose(it) }
        sessions.clear()
    }

    companion object {
        const val GIT_CHAT_TITLE = "Git"

        private const val CHAT_TITLE_PREFIX = "Chat "

        fun getInstance(project: Project): ChatSessionManager = project.service()
    }
}
