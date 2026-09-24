package dev.lain.claudejb.view.window

import com.intellij.openapi.application.ApplicationManager
import dev.lain.claudejb.controller.commands.git.GitIntegration
import dev.lain.claudejb.controller.vuln.VulnService
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.util.edt
import dev.lain.claudejb.view.payload.chat.JcefCardPayload
import dev.lain.claudejb.view.payload.chat.JcefState
import dev.lain.claudejb.view.payload.menu.SettingsMenuRows
import dev.lain.claudejb.view.payload.panel.JcefAccountData
import dev.lain.claudejb.view.payload.panel.JcefSessionData
import kotlinx.serialization.json.JsonObject
import java.util.EnumMap
import java.util.EnumSet

internal class ChatSnapshots(private val presenter: ChatPresenter) {

    enum class Kind { META, STATE, SESSION, MENU, THEME, PERMISSIONS, TABS }

    private val dirty = EnumSet.noneOf(Kind::class.java)
    private var scheduled = false
    private val last = EnumMap<Kind, String>(Kind::class.java)

    @Volatile
    private var account: JsonObject? = null

    private var accountReadAt = 0L

    private var accountReading = false

    private val session get() = presenter.session
    private val project get() = presenter.project

    fun mark(vararg kinds: Kind) {
        synchronized(dirty) {
            dirty.addAll(kinds)
            if (scheduled) return
            scheduled = true
        }
        edt(project) { flush() }
    }

    fun now(vararg kinds: Kind) = kinds.forEach(::build)

    private fun flush() {
        val due = synchronized(dirty) {
            scheduled = false
            EnumSet.copyOf(dirty).also { dirty.clear() }
        }
        due.forEach(::build)
    }

    private fun build(kind: Kind) {
        when (kind) {
            Kind.META -> send(kind, "meta", JcefState.metaJson(session))

            Kind.STATE -> send(kind, "state", JcefState.stateJson(session, presenter.feed.usage))

            Kind.SESSION -> send(kind, "session", sessionJson())

            Kind.MENU -> {
                send(kind, "settingsMenu", menuJson())
                build(Kind.THEME)
            }

            Kind.THEME -> send(kind, "theme", themeJson())

            Kind.PERMISSIONS -> presenter.exec("permissions", permissionsJson())

            Kind.TABS -> presenter.agentTabs.render()
        }
    }

    private fun send(kind: Kind, method: String, json: String) {
        if (last[kind] == json) return
        last[kind] = json
        presenter.exec(method, json)
    }

    private fun sessionJson(): String {
        readAccountSoon()
        return JcefSessionData.sessionJson(
            session,
            windowMinutes = ClaudeSettings.getInstance(project).workloadWindowMinutes,
            nowMillis = System.currentTimeMillis(),
            usage = presenter.feed.usage,
            workloads = presenter.registry.workloads(),
            plan = presenter.feed.plan,
            git = GitIntegration.getInstance(project).snapshot(),
            vuln = VulnService.getInstance(project).snapshot(),
            account = account,
        )
    }

    private fun readAccountSoon() {
        val now = System.currentTimeMillis()
        if (accountReading || now - accountReadAt < ACCOUNT_REFRESH_MS) return
        accountReading = true
        accountReadAt = now
        ApplicationManager.getApplication().executeOnPooledThread {
            val fresh = runCatching { JcefAccountData.accountJson(session) }.getOrNull()
            edt(project) {
                accountReading = false
                if (fresh == account) return@edt
                account = fresh
                mark(Kind.SESSION)
            }
        }
    }

    private fun menuJson(): String {
        val settings = ClaudeSettings.getInstance(project)
        return "{\"items\":" + SettingsMenuRows.json(settings.scope.id, settings.state, session) + "}"
    }

    private fun themeJson(): String =
        "{\"reducedMotion\":" + ClaudeSettings.getInstance(project).reduceMotion + "}"

    private fun permissionsJson(): String {
        val perms = session.cards.pending()
        val groups = listOf(JcefCardPayload.Group(perms, diffByRequest = presenter.edits.diffsFor(perms))) +
            presenter.gitChat.permissionGroup()
        return JcefCardPayload.permissionsJson(groups)
    }

    private companion object {
        const val ACCOUNT_REFRESH_MS = 5_000L
    }
}
