package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class ProjectProblems private constructor(
    private val files: Map<String, List<JsonObject>>,
    private val other: List<String>,
    private val total: Int,
) {

    fun toJson(max: Int): JsonObject = buildJsonObject {
        put("count", total)
        put("truncated", total > max)
        put(
            "files",
            buildJsonArray {
                for ((file, problems) in files) {
                    add(
                        buildJsonObject {
                            put("file", file)
                            put("problems", buildJsonArray { problems.forEach { add(it) } })
                        },
                    )
                }
            },
        )
        if (other.isNotEmpty()) put("other", buildJsonArray { other.forEach { add(JsonPrimitive(it)) } })
    }

    companion object {

        fun snapshot(project: Project, group: String?, max: Int): ProjectProblems {
            val collector = ProblemsCollector.getInstance(project)
            val files = LinkedHashMap<String, MutableList<JsonObject>>()
            val other = ArrayList<String>()
            var total = 0
            for (file in collector.getProblemFiles()) {
                for (problem in collector.getFileProblems(file)) {
                    if (!wanted(problem, group)) continue
                    if (total++ < max) files.getOrPut(Locations.relative(project, file)) { ArrayList() } += row(problem)
                }
            }
            for (problem in collector.getOtherProblems()) {
                if (!wanted(problem, group)) continue
                if (total++ < max) other += problem.text
            }
            return ProjectProblems(files, other, total)
        }

        private fun wanted(problem: Problem, group: String?): Boolean =
            group == null || problem.group?.contains(group, ignoreCase = true) == true

        private fun row(problem: Problem): JsonObject = buildJsonObject {
            put("line", (problem as? FileProblem)?.line?.takeIf { it >= 0 }?.plus(1) ?: 0)
            put("message", problem.text)
        }
    }
}
