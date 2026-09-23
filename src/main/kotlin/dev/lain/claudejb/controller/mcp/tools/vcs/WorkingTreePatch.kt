package dev.lain.claudejb.controller.mcp.tools.vcs

import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import dev.lain.claudejb.model.mcp.Clip
import dev.lain.claudejb.model.mcp.ToolException
import java.io.StringWriter

internal object WorkingTreePatch {

    class Unified(val files: Int, val lines: List<String>, val truncated: Boolean)

    const val MAX_LINE = 1_000

    fun unified(project: Project, path: String?, maxLines: Int): Unified {
        val manager = ChangeListManager.getInstance(project)
        val changes = if (path == null) manager.allChanges else manager.getChangesIn(VcsPaths.filePath(project, path))
        val lines = ArrayList<String>()
        var truncated = false
        for (change in changes) {
            if (lines.size >= maxLines) {
                truncated = true
                break
            }
            for (line in stripIdeHeaders(patchOf(project, change).trimEnd('\n')).lineSequence()) {
                if (lines.size >= maxLines) {
                    truncated = true
                    break
                }
                lines += Clip.line(line, MAX_LINE)
            }
        }
        return Unified(changes.size, lines, truncated)
    }

    private fun patchOf(project: Project, change: Change): String {
        val patches = try {
            IdeaTextPatchBuilder.buildPatch(project, listOf(change), VcsPaths.base(project), false)
        } catch (e: VcsException) {
            throw ToolException("the IDE could not build the diff: ${e.message}", e)
        }
        val writer = StringWriter()
        UnifiedDiffWriter.write(project, patches, writer, "\n", null)
        return writer.toString()
    }

    fun stripIdeHeaders(patch: String): String =
        patch.lineSequence().filterNot { line -> IDE_HEADERS.any { line.startsWith(it) } }.joinToString("\n")

    private val IDE_HEADERS = listOf("Index: ", "IDEA additional info:", "Subsystem: ", "<+>", "====")
}
