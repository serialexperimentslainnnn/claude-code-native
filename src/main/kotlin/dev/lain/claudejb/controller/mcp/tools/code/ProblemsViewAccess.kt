package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface ProblemsViewAccess {

    class Entry(val file: VirtualFile?, val line: Int, val text: String, val group: String?, val description: String?)

    class Tab(val id: String, val title: String)

    fun entries(): List<Entry>

    fun tabs(): List<Tab>?

    fun selectedTab(): String?

    companion object {
        const val MISSING = "this IDE build does not expose the Problems view to plugins, so its problems cannot be read here"

        fun of(project: Project): ProblemsViewAccess? =
            project.serviceOrNull<ProblemsViewAccess>() ?: CoreProblemsView(project).takeIf { coreLoads }

        private val coreLoads: Boolean by lazy {
            runCatching { Class.forName(CORE_CLASS, false, ProblemsViewAccess::class.java.classLoader) }.isSuccess
        }

        private const val CORE_CLASS = "com.intellij.analysis.problemsView.ProblemsCollector"
    }
}
