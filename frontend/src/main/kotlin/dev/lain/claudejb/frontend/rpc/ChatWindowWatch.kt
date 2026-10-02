package dev.lain.claudejb.frontend.rpc

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ChatWindowWatch : ProjectActivity {
    override suspend fun execute(project: Project) = project.service<ChatClient>().watchWindow()
}
