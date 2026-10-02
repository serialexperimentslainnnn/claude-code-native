package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content

interface ProblemsViewAccess {

    class Entry(val file: VirtualFile?, val line: Int, val text: String, val group: String?, val description: String?)

    class Tab(val id: String, val title: String)

    fun entries(): List<Entry>

    fun tabs(): List<Tab>?

    fun selectedTab(): String?

    fun window(): ToolWindow?

    fun content(tabId: String): Content?

    companion object {
        const val MISSING = "this IDE build does not expose the Problems view to plugins, so its problems cannot be read here"

        fun of(project: Project): ProblemsViewAccess? =
            project.serviceOrNull<ProblemsViewAccess>() ?: if (coreLoads) CoreProblemsView(project) else null

        private val coreLoads: Boolean by lazy {
            CORE_CLASSES.all { runCatching { Class.forName(it, false, ProblemsViewAccess::class.java.classLoader) }.isSuccess }
        }

        private val CORE_CLASSES = listOf(
            "com.intellij.analysis.problemsView.ProblemsCollector",
            "com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils",
        )
    }
}
