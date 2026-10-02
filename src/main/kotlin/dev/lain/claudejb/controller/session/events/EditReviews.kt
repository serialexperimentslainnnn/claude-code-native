package dev.lain.claudejb.controller.session.events

import com.intellij.util.concurrency.AppExecutorUtil
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.diff.DiffPresenter
import dev.lain.claudejb.model.diff.EditSnapshot
import dev.lain.claudejb.model.mcp.OwnTools
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class EditReviews(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val worker: Executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(WORKER, 1),
) {

    private val reviewing = ConcurrentHashMap.newKeySet<String>()

    fun capture(toolName: String, input: JsonObject, toolUseId: String) {
        reviewing.add(toolUseId)
        worker.execute { s.diffs.captureForReview(toolName, input, toolUseId) }
    }

    fun capture(review: OwnTools.Review, toolUseId: String) {
        reviewing.add(toolUseId)
        worker.execute { asWrite(review)?.let { s.diffs.captureForReview(it.toolName, it.input, toolUseId) } }
    }

    fun settle(toolUseId: String, show: (String?) -> Unit) {
        if (reviewing.remove(toolUseId)) {
            worker.execute { deliver(s.diffs.onToolResult(toolUseId), show) }
            return
        }
        val snapshot = s.diffs.onToolResult(toolUseId) ?: return show(null)
        worker.execute { deliver(snapshot, show) }
    }

    fun forget(toolUseId: String) {
        reviewing.remove(toolUseId)
        s.diffs.onToolResult(toolUseId)
    }

    private fun deliver(snapshot: EditSnapshot?, show: (String?) -> Unit) {
        val diff = snapshot?.let(::diffOf)
        edt { show(diff) }
    }

    private fun asWrite(review: OwnTools.Review): OwnTools.Review? {
        if (review.toolName != OwnTools.INSERT) return review
        val path = DiffPresenter.filePathOf(review.input) ?: return null
        val before = DiffPresenter.readCurrent(path, s.project.basePath) ?: return null
        return OwnTools.asWrite(review, before)
    }

    private fun diffOf(snap: EditSnapshot): String? = DiffPresenter.proposedContent(snap.toolName, snap.input, snap.beforeText)
        ?.takeIf { it.length <= DiffPresenter.MAX_DIFF_FILE_BYTES }
        ?.let { DiffPresenter.unifiedDiff(snap.beforeText, it) }
        ?.takeIf { it.isNotBlank() }

    private companion object {
        const val WORKER = "Claude Code edit review"
    }
}
