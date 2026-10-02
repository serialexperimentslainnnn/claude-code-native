package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.controller.commands.PromptInNewChat
import dev.lain.claudejb.controller.vuln.VulnPromptedActions
import dev.lain.claudejb.controller.vuln.VulnService
import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.util.thisLogger
import dev.lain.claudejb.view.payload.JcefVulnData
import dev.lain.claudejb.view.window.ChatPresenter
import dev.lain.claudejb.view.window.ChatSnapshots.Kind

internal class BridgeVuln(private val presenter: ChatPresenter) {

    private val log = thisLogger()

    private val service: VulnService get() = VulnService.getInstance(presenter.project)

    private val repaint = { presenter.snapshots.mark(Kind.SESSION) }

    fun handle(m: Msg.Vuln) {
        when (m) {
            Msg.OpenVulnView -> presenter.security.showVulnView()
            is Msg.VulnConsentChoice -> service.setConsent(m.granted, repaint)
            Msg.VulnScan -> service.scan(repaint)
            Msg.VulnCancel -> service.cancel(repaint)
            Msg.VulnInventoryRequest -> inventory()
            is Msg.VulnFix -> fix(m.findingId)
            is Msg.VulnPlan -> plan(m.tiers)
        }
    }

    private fun inventory() {
        val current = service
        val endpoint = current.snapshot().endpoint
        presenter.execBuilt("vulnInventory") { JcefVulnData.inventoryJson(current.inventory(), endpoint).toString() }
    }

    private fun fix(findingId: String) {
        val finding = service.finding(findingId)
        if (finding == null) {
            log.warn("The security view asked to fix a finding that is no longer in the last report: $findingId")
            return
        }
        val text = VulnPromptedActions.updatePrompt(finding)
        if (text == null) {
            log.warn("Refusing to prompt for '$findingId': the advisory or the manifest carries unquotable text")
            return
        }
        inNewChat(PromptInNewChat.title("Update", finding.component.name), text)
    }

    private fun plan(tiers: List<String>) {
        val report = service.snapshot().report
        if (report == null) {
            log.warn("The security view asked to plan without a report to plan from")
            return
        }
        val wanted = report.ordered().filter { tiers.isEmpty() || it.tier.wire in tiers }
        val text = VulnPromptedActions.planPrompt(wanted)
        if (text == null) {
            log.warn("Refusing to plan: every finding carries text this build will not quote")
            return
        }
        val subject = if (wanted.size == 1) "1 vulnerable dependency" else "${wanted.size} vulnerable dependencies"
        inNewChat(PromptInNewChat.title("Plan", subject), text)
    }

    private fun inNewChat(title: String, text: String) {
        if (!PromptInNewChat.open(presenter.project, title, text)) presenter.session.send(text)
    }
}
