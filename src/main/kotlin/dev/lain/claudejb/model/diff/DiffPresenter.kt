package dev.lain.claudejb.model.diff

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.DumbProgressIndicator
import dev.lain.claudejb.model.protocol.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

object DiffPresenter {

    val REVIEWABLE_TOOLS = setOf("Edit", "Write", "MultiEdit")

    fun filePathOf(input: JsonObject): String? = input.str("file_path")

    fun isWithinRoot(path: String?, projectRoot: String?): Boolean {
        if (path == null || projectRoot == null) return false
        return try {
            val canonicalFile = File(path).canonicalFile
            val canonicalRoot = File(projectRoot).canonicalFile
            val rootPath = canonicalRoot.path
            val filePath = canonicalFile.path
            filePath == rootPath || filePath.startsWith(rootPath + File.separator)
        } catch (_: java.io.IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    const val MAX_DIFF_FILE_BYTES = 1_000_000L

    fun readCurrent(path: String, projectRoot: String?): String? {
        if (!isWithinRoot(path, projectRoot)) return null
        val file = File(path)
        if (!file.isFile) return ""
        if (file.length() > MAX_DIFF_FILE_BYTES) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun proposedContent(toolName: String, input: JsonObject, currentText: String): String? = when (toolName) {
        "Write" -> input.str("content") ?: ""

        "Edit" -> applyEdit(currentText, input)

        "MultiEdit" -> {
            val edits = input["edits"] as? JsonArray ?: return null
            edits.fold(currentText) { acc, element -> applyEdit(acc, element.jsonObject) ?: acc }
        }

        else -> null
    }

    private fun applyEdit(text: String, edit: JsonObject): String? {
        val old = edit.str("old_string") ?: return null
        val new = edit.str("new_string") ?: ""
        val replaceAll = (edit["replace_all"] as? JsonPrimitive)?.booleanOrNull ?: false
        return if (replaceAll) text.replace(old, new) else text.replaceFirst(old, new)
    }

    internal fun diffTitle(fileName: String) = "$fileName — Claude"

    fun computeHunks(current: String, proposed: String): List<Hunk> {
        val fragments = ComparisonManager.getInstance()
            .compareLines(
                current,
                proposed,
                ComparisonPolicy.DEFAULT,
                DumbProgressIndicator.INSTANCE,
            )
        return fragments.map { Hunk(it.startLine1, it.endLine1, it.startLine2, it.endLine2) }
    }

    fun unifiedDiff(current: String, proposed: String, context: Int = 3): String {
        val hunks = computeHunks(current, proposed)
        if (hunks.isEmpty()) return ""
        return UnifiedDiff.format(current.split("\n"), proposed.split("\n"), hunks, context)
    }
}

data class Hunk(
    val start1: Int,
    val end1: Int,
    val start2: Int,
    val end2: Int,
)
