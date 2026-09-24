package dev.lain.claudejb.frontend.rpc

import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

internal class ChatWindowVisibility(private val report: (Boolean) -> Unit) : ToolWindowManagerListener {

    private var last: Boolean? = null

    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val visible = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)?.isVisible == true
        if (visible == last) return
        last = visible
        report(visible)
    }

    companion object {
        const val TOOL_WINDOW_ID = "Claude Code"
    }
}
