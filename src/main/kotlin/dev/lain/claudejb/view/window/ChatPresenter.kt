package dev.lain.claudejb.view.window

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.bridge.ChatBridgeRouter
import dev.lain.claudejb.controller.bridge.ChatLinks
import dev.lain.claudejb.controller.commands.OnboardingController
import dev.lain.claudejb.controller.session.AttentionLanding
import dev.lain.claudejb.controller.session.AttentionReason
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.controller.session.SessionListener
import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.model.context.Attachment
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.ChatRef
import dev.lain.claudejb.rpc.FrontendChannel
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.util.edt
import dev.lain.claudejb.util.logger
import dev.lain.claudejb.view.feed.AttachmentTray
import dev.lain.claudejb.view.feed.ChatAgentTabs
import dev.lain.claudejb.view.feed.ChatEditReview
import dev.lain.claudejb.view.feed.ChatTranscriptView
import dev.lain.claudejb.view.feed.SecurityViews
import dev.lain.claudejb.view.feed.SessionFeed
import dev.lain.claudejb.view.git.GitChatFeed
import dev.lain.claudejb.view.guard.GuardFeed
import dev.lain.claudejb.view.log.LogFeed
import dev.lain.claudejb.view.window.ChatSnapshots.Kind

internal class ChatPresenter(
    val project: Project,
    val session: ClaudeSession,
    val id: ChatId,
    val registry: ChatRegistry,
) : Disposable, SessionListener {

    val pushes = PushStream { edt(project) { transcript.resync() } }

    val router = ChatBridgeRouter(this)

    val links = ChatLinks(project) { exec(FrontendChannel.BROWSE, JcefBridge.jsString(it)) }

    val transcript = ChatTranscriptView(session, ::emit)

    val frontend = FrontendPushes(::exec)

    val tray = AttachmentTray(project, ::emit, frontend::focusInput)

    val edits = ChatEditReview(project, session, tray::notify)

    val snapshots = ChatSnapshots(this)

    val feed = SessionFeed(session, ::emit) { snapshots.mark(Kind.META, Kind.STATE, Kind.SESSION) }

    val onboarding = OnboardingController(project, session, PageScriptCalls.into(::emit))

    val agentTabs = ChatAgentTabs(this)

    val gitChat = GitChatFeed(this, ::emit)

    val guard = GuardFeed(this)

    val logFeed = LogFeed(this)

    val security = SecurityViews(this)

    var attention: Boolean = false

    var lastNotified: Long = 0L

    private val pendingUntilReady = mutableListOf<() -> Unit>()

    private var wasRunning = false

    private var wasTurnActive = false

    init {
        agentTabs.render()
        session.agentScanner.scan()

        session.transcript.addListener(transcript)
        session.addListener(this)
        session.login.attachUi(onboarding)

        frontend.pushVibe()
        snapshots.now(*Kind.entries.toTypedArray())
        tray.push()
        security.pushVuln()
        whenReady(feed::onSessionReady)
        feed.start()
        transcript.fullResync()
        registry.git.request()
    }

    fun ref(): ChatRef = ChatRef(id, session.title)

    fun emit(push: PagePush) = pushes.emit(push)

    fun exec(method: String, json: String) = emit(PagePush(method, json))

    fun execBuilt(method: String, build: () -> String?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val payload = runCatching(build)
                .onFailure { LOG.warn("Claude Code: $method could not be answered", it) }
                .getOrNull() ?: return@executeOnPooledThread
            exec(method, payload)
        }
    }

    fun replay() = pushes.replay()

    fun refreshPage() {
        feed.requestMcp()
        feed.requestVersion()
        agentTabs.render()
        registry.git.request()
        security.pushGuard()
        security.pushVuln()
    }

    override fun onAgentsChanged(freshlyAdmitted: List<String>) {
        agentTabs.onAgentsScanned(freshlyAdmitted)
        transcript.refreshShown()
        snapshots.mark(Kind.SESSION)
    }

    override fun onStateChanged() {
        snapshots.mark(Kind.META, Kind.STATE, Kind.SESSION, Kind.MENU, Kind.TABS)
        if (transcript.showsTask) transcript.refreshShown()
        drainPendingUntilReady()
        val running = session.isRunning()
        if (running && !wasRunning) feed.onSessionReady()
        wasRunning = running
        if (session.signals.rateLimits.isNotEmpty()) feed.requestUsage()
        val turnActive = session.turn.active
        if (wasTurnActive && !turnActive && running) {
            feed.requestPlan()
            registry.git.request()
        }
        wasTurnActive = turnActive
        onboarding.onStateChanged()
    }

    override fun onMetadataChanged() = snapshots.mark(Kind.META, Kind.STATE, Kind.SESSION)

    override fun onPermissionsChanged() = snapshots.mark(Kind.PERMISSIONS)

    override fun onTitleChanged() = registry.onTitleChanged(this)

    override fun onAttention(reason: AttentionReason, landing: AttentionLanding) =
        registry.attention.on(this, reason, landing)

    private fun whenReady(action: () -> Unit) {
        if (session.isRunning()) {
            action()
            return
        }
        pendingUntilReady += action
    }

    private fun drainPendingUntilReady() {
        if (pendingUntilReady.isEmpty() || !session.isRunning()) return
        val queued = pendingUntilReady.toList()
        pendingUntilReady.clear()
        queued.forEach { it() }
    }

    fun cardSession(scope: String): ClaudeSession =
        if (scope == JcefBridge.SCOPE_GIT) gitChat.session() else session

    fun openDashboard() {
        snapshots.now(Kind.SESSION)
        security.pushVuln()
        feed.requestMcp()
        feed.requestVersion()
        feed.requestUsage()
        exec("openDashboard", PushStream.NO_ARGS)
    }

    fun showGitView() {
        registry.git.request()
        exec("showGitView", PushStream.NO_ARGS)
    }

    fun mentionCurrentFile() = tray.addCurrentFile()

    fun addAttachment(attachment: Attachment) = tray.add(attachment)

    override fun dispose() {
        session.transcript.removeListener(transcript)
        session.removeListener(this)
        session.login.detachUi(onboarding)
        onboarding.dispose()
        transcript.stop()
        feed.stop()
        gitChat.dispose()
        pushes.close()
    }

    private companion object {
        val LOG = logger<ChatPresenter>()
    }
}
