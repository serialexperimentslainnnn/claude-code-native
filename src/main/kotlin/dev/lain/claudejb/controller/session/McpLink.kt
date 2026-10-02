package dev.lain.claudejb.controller.session

import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.mcp.IdeMcpService

object McpLink {

    fun admitReconnect(project: Project, name: String) {
        IdeMcpService.getInstance(project).admitReconnect(name)
    }

    fun prewarm(project: Project) = IdeMcpService.getInstance(project).prewarm()
}
