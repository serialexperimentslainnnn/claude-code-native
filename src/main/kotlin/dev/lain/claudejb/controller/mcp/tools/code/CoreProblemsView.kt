package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.project.Project

internal class CoreProblemsView(private val project: Project) : ProblemsViewAccess {

    override fun entries(): List<ProblemsViewAccess.Entry> {
        val collector = ProblemsCollector.getInstance(project)
        val inFiles = collector.getProblemFiles().flatMap { file ->
            collector.getFileProblems(file).map { problem ->
                val line = (problem as? FileProblem)?.line ?: -1
                ProblemsViewAccess.Entry(file, line, problem.text, problem.group, problem.description)
            }
        }
        val other = collector.getOtherProblems().map { ProblemsViewAccess.Entry(null, -1, it.text, it.group, it.description) }
        return inFiles + other
    }

    override fun tabs(): List<ProblemsViewAccess.Tab>? {
        val window = ProblemsViewToolWindowUtils.getToolWindow(project) ?: return null
        return window.contentManager.contents.mapNotNull { content ->
            val tab = content.component as? ProblemsViewTab ?: return@mapNotNull null
            ProblemsViewAccess.Tab(tab.getTabId(), content.displayName ?: tab.getName(0))
        }
    }

    override fun selectedTab(): String? = ProblemsViewToolWindowUtils.getSelectedTab(project)?.getTabId()
}
