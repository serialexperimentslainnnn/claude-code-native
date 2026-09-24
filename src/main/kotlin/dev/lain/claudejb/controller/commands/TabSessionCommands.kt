package dev.lain.claudejb.controller.commands

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.concurrency.AppExecutorUtil
import dev.lain.claudejb.controller.session.ChatSessionManager
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.controller.session.history.SessionHistory
import dev.lain.claudejb.model.session.history.SessionListing
import dev.lain.claudejb.model.session.history.SessionStore
import dev.lain.claudejb.model.session.history.SessionTitleReader
import dev.lain.claudejb.model.session.history.SessionTranscriptReader
import dev.lain.claudejb.model.session.transcript.EntryDTO
import dev.lain.claudejb.model.session.transcript.SessionRef
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.util.PluginIdentity
import dev.lain.claudejb.util.edt
import dev.lain.claudejb.view.window.ChatRegistry
import javax.swing.JList

internal class TabSessionCommands(
    private val project: Project,
    private val registry: ChatRegistry,
) {

    private fun openChat(session: ClaudeSession) = registry.open(session, true)

    fun newChatWith(title: String, prompt: String) {
        val session = ChatSessionManager.getInstance(project).create()
        session.title = title
        openChat(session)
        session.send(prompt)
    }

    private fun activeSession(): ClaudeSession? = registry.selected()?.session

    fun restoreOrCreate() {
        val manager = ChatSessionManager.getInstance(project)
        val quiet = !registry.isEmpty()
        ClaudeSettings.getInstance(project).warm().thenAcceptAsync({ settings ->
            if (!settings.restoreOpenChatsOnStartup) {
                edt(project) { if (registry.isEmpty()) openChat(manager.create()) }
                return@thenAcceptAsync
            }
            val ids = SessionHistory.getInstance(project).openSessions()
                .distinct()
                .filter { SessionStore.exists(it) }
                .ifEmpty { listOfNotNull(SessionListing.list(project).firstOrNull()?.sessionId) }
            if (ids.isEmpty()) {
                edt(project) { if (registry.isEmpty()) openChat(manager.create()) }
                return@thenAcceptAsync
            }
            ids.forEachIndexed { index, id ->
                val title = SessionTitleReader.readTitle(id)
                val entries = SessionTranscriptReader.readEntries(id, SessionTranscriptReader.DEFAULT_RESTORE_CAP, project.basePath)
                edt(project) { restore(manager, id, title, entries, select = !quiet && index == ids.lastIndex) }
            }
        }, AppExecutorUtil.getAppExecutorService())
    }

    private fun restore(manager: ChatSessionManager, id: String, title: String?, entries: List<EntryDTO>, select: Boolean) {
        if (opened(id) != null) return
        val s = manager.create()
        s.title = title ?: s.title
        s.persistence.restore(id, entries)
        registry.open(s, select)
    }

    fun renameActiveSession() {
        val session = activeSession() ?: return
        val input = Messages.showInputDialog(
            project,
            "New session name:",
            "Rename Session",
            null,
            session.title,
            null,
        )?.trim().orEmpty()
        if (input.isEmpty() || input == session.title) return
        session.persistence.rename(input)
    }

    fun forkActiveSession() {
        val source = activeSession() ?: return
        val sourceId = source.sessionId ?: run {
            Messages.showInfoMessage(project, "This session hasn't been initialized yet — nothing to fork.", "Claude Code")
            return
        }
        val sourceTitle = source.title
        ApplicationManager.getApplication().executeOnPooledThread {
            val entries = SessionTranscriptReader.readEntries(
                sourceId,
                SessionTranscriptReader.DEFAULT_RESTORE_CAP,
                project.basePath,
            )
            edt {
                val manager = ChatSessionManager.getInstance(project)
                val s = manager.create()
                s.title = "$sourceTitle (fork)"
                s.persistence.restore(sourceId, entries, fork = true)
                openChat(s)
            }
        }
    }

    fun openPreviousSession() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val refs = SessionListing.list(project)
            ApplicationManager.getApplication().invokeLater({ if (!project.isDisposed) choosePrevious(refs) }, ModalityState.nonModal())
        }
    }

    private fun choosePrevious(refs: List<SessionRef>) {
        if (refs.isEmpty()) {
            Messages.showInfoMessage(project, "No previous sessions have been saved yet.", "Claude Code")
            return
        }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(refs)
            .setTitle("Open Previous Session")
            .setRenderer(SessionRefRenderer())
            .setItemChosenCallback(::reopen)
            .setRequestFocus(true)
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    fun reopen(ref: SessionRef) {
        if (revealOpened(ref.sessionId)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            val entries = SessionTranscriptReader.readEntries(
                ref.sessionId,
                SessionTranscriptReader.DEFAULT_RESTORE_CAP,
                project.basePath,
            )
            edt(project) {
                if (revealOpened(ref.sessionId)) return@edt
                val s = ChatSessionManager.getInstance(project).create()
                s.title = ref.title
                s.persistence.restore(ref.sessionId, entries)
                openChat(s)
            }
        }
    }

    private fun revealOpened(sessionId: String): Boolean {
        val tab = opened(sessionId) ?: return false
        if (registry.selected() === tab) alreadyOpen(tab.session.title)
        registry.reveal(tab)
        return true
    }

    private fun alreadyOpen(title: String) =
        NotificationGroupManager.getInstance().getNotificationGroup(PluginIdentity.NOTIFICATION_GROUP)
            .createNotification("\"$title\" is already open in the chat on screen.", NotificationType.INFORMATION)
            .notify(project)

    private fun opened(sessionId: String) =
        registry.all().firstOrNull { it.session.sessionId == sessionId && !it.session.launch.fork }

    private class SessionRefRenderer : SimpleListCellRenderer<SessionRef>() {
        override fun customize(
            list: JList<out SessionRef>,
            value: SessionRef?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            value ?: return
            val parts = buildList {
                value.gitBranch?.let { add(escapeHtml(it)) }
                value.createdAt?.let { add(escapeHtml(formatCreatedAt(it))) }
                value.firstPrompt?.let { add(escapeHtml(truncate(it.replace('\n', ' '), PROMPT_PREVIEW_MAX))) }
            }
            val sub = if (parts.isEmpty()) {
                ""
            } else {
                "<br><font color='#888888'>${parts.joinToString("  ·  ")}</font>"
            }
            text = "<html>${escapeHtml(value.title)}  —  ${relativeTime(value.lastModified)}$sub</html>"
        }
    }

    companion object {
        private const val PROMPT_PREVIEW_MAX = 60

        private const val MILLIS_PER_SECOND = 1000
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 3600
        private const val SECONDS_PER_DAY = 86_400

        private fun formatCreatedAt(iso: String): String =
            runCatching { java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }
                .getOrDefault(iso)

        fun truncate(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1) + "…"

        private fun escapeHtml(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
            val secs = (now - timestamp).coerceAtLeast(0) / MILLIS_PER_SECOND
            return when {
                secs < SECONDS_PER_MINUTE -> "just now"
                secs < SECONDS_PER_HOUR -> "${secs / SECONDS_PER_MINUTE}m ago"
                secs < SECONDS_PER_DAY -> "${secs / SECONDS_PER_HOUR}h ago"
                else -> "${secs / SECONDS_PER_DAY}d ago"
            }
        }
    }
}
