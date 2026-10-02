package dev.lain.claudejb.view.feed

import dev.lain.claudejb.controller.vuln.VulnService
import dev.lain.claudejb.view.window.ChatPresenter
import dev.lain.claudejb.view.window.ChatSnapshots.Kind
import dev.lain.claudejb.view.window.PushStream

internal class SecurityViews(private val presenter: ChatPresenter) {

    fun pushGuard() = presenter.guard.push()

    fun pushVuln() = VulnService.getInstance(presenter.project).refresh { presenter.snapshots.mark(Kind.SESSION) }

    fun openGuardView() {
        pushGuard()
        presenter.exec("openGuardView", PushStream.NO_ARGS)
    }

    fun showVulnView() {
        pushVuln()
        presenter.exec("showVulnView", PushStream.NO_ARGS)
    }
}
