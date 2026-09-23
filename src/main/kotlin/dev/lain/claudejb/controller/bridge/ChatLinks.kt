package dev.lain.claudejb.controller.bridge

import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.context.LinkNavigator

internal class ChatLinks(project: Project, private val browse: (String) -> Unit) {

    private val navigator = LinkNavigator(project)

    fun open(url: String) {
        val link = url.trim()
        if (link.lowercase().startsWith("https://")) browse(link) else navigator.open(link)
    }
}
