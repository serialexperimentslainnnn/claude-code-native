package dev.lain.claudejb.view.window

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.session.AttentionLanding
import dev.lain.claudejb.controller.session.AttentionReason

internal class ChatAttention(private val project: Project, private val registry: ChatRegistry) {

    fun on(presenter: ChatPresenter, reason: AttentionReason, landing: AttentionLanding) {
        if (registry.onScreen(presenter) && showsWhereItLanded(presenter, reason, landing)) return

        registry.badge(presenter)

        val now = System.currentTimeMillis()
        if (now - presenter.lastNotified <= NOTIFY_THROTTLE_MS) return
        presenter.lastNotified = now

        val title = presenter.session.title
        val text = when (reason) {
            AttentionReason.PERMISSION -> "Claude needs your approval in \"$title\"."
            AttentionReason.TURN_DONE -> "Claude finished responding in \"$title\"."
            AttentionReason.ERROR -> "Claude hit an error in \"$title\"."
            AttentionReason.GUARD_BLOCKED -> "The guard blocked a tool call in \"$title\"."
        }
        NotificationGroupManager.getInstance().getNotificationGroup("Claude Code")
            .createNotification("Claude Code", text, typeFor(reason))
            .addAction(
                NotificationAction.createSimpleExpiring("Open") {
                    registry.presenter(presenter.id)?.let(registry::reveal)
                },
            )
            .notify(project)
    }

    private fun showsWhereItLanded(presenter: ChatPresenter, reason: AttentionReason, landing: AttentionLanding): Boolean =
        reason != AttentionReason.GUARD_BLOCKED || presenter.transcript.shows(landing)

    private fun typeFor(reason: AttentionReason): NotificationType = when (reason) {
        AttentionReason.ERROR -> NotificationType.ERROR
        AttentionReason.GUARD_BLOCKED -> NotificationType.WARNING
        else -> NotificationType.INFORMATION
    }

    private companion object {
        const val NOTIFY_THROTTLE_MS = 3000L
    }
}
