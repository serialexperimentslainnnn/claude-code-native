package dev.lain.claudejb.controller.actions

import com.intellij.openapi.project.Project
import dev.lain.claudejb.model.context.Attachment
import dev.lain.claudejb.view.window.ChatRegistry

object AttachmentActions {

    fun pin(project: Project, attachment: Attachment) {
        val registry = ChatRegistry.getInstance(project)
        registry.selectedOrNew().addAttachment(attachment)
        registry.showToolWindow()
    }
}
