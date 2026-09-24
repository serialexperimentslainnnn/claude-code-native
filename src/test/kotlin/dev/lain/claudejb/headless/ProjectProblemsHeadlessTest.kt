package dev.lain.claudejb.headless

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.mcp.Reveal
import dev.lain.claudejb.controller.mcp.tools.code.DiagnosticsTools
import dev.lain.claudejb.model.mcp.ToolArgs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class ProjectProblemsHeadlessTest : BasePlatformTestCase() {

    private class KnownError(override val project: Project) : ProblemsProvider

    private class Unresolved(override val provider: ProblemsProvider, override val file: VirtualFile) : FileProblem {
        override val text: String = MESSAGE
        override val line: Int = 2
    }

    override fun runInDispatchThread(): Boolean = false

    fun `test project_problems reports the error the Problems view holds for a file`() {
        val file = myFixture.addFileToProject("src/Broken.kt", "fun main() {\n\n    brokenCall()\n}\n").virtualFile
        val problem = Unresolved(KnownError(project), file)
        val collector = ProblemsCollector.getInstance(project)
        collector.problemAppeared(problem)
        try {
            val tool = DiagnosticsTools(project, Reveal(project)).domain().tools.single { it.spec.name == "project_problems" }
            val answer = runBlocking { tool.run(ToolArgs(JsonObject(emptyMap()))) }
            assertFalse(answer.text, answer.isError)
            assertTrue(answer.text, answer.text.contains("src/Broken.kt"))
            assertTrue(answer.text, answer.text.contains("3,$MESSAGE"))
        } finally {
            collector.problemDisappeared(problem)
        }
    }

    private companion object {
        const val MESSAGE = "Unresolved reference brokenCall"
    }
}
