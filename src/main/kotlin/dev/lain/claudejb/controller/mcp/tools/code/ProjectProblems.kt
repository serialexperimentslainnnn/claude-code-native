package dev.lain.claudejb.controller.mcp.tools.code

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

        fun snapshot(project: Project, entries: List<ProblemsViewAccess.Entry>, group: String?, max: Int): ProjectProblems {
            val files = LinkedHashMap<String, MutableList<JsonObject>>()
            val other = ArrayList<String>()
            var total = 0
            for (entry in entries) {
                if (!wanted(entry, group)) continue
                if (total++ >= max) continue
                val file = entry.file
                if (file == null) other += entry.text else files.getOrPut(Locations.relative(project, file)) { ArrayList() } += row(entry)
            }
            return ProjectProblems(files, other, total)
        }

        private fun wanted(entry: ProblemsViewAccess.Entry, group: String?): Boolean =
            group == null || entry.group?.contains(group, ignoreCase = true) == true

        private fun row(entry: ProblemsViewAccess.Entry): JsonObject = buildJsonObject {
            put("line", entry.line.takeIf { it >= 0 }?.plus(1) ?: 0)
            put("message", entry.text)
        }
    }
}
