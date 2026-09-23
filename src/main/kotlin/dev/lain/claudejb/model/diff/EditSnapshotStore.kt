package dev.lain.claudejb.model.diff

import kotlinx.serialization.json.JsonObject
import java.io.File

data class EditSnapshot(
    val toolName: String,
    val input: JsonObject,
    val beforeText: String,
    val filePath: String,
    val existedBefore: Boolean = true,
)

class EditSnapshotStore(private val maxBytes: Long = DEFAULT_MAX_BYTES) {

    private val byToolUseId = LinkedHashMap<String, EditSnapshot>(INITIAL_BUCKETS, LOAD_FACTOR, true)

    private var bytes = 0L

    fun capture(toolName: String, input: JsonObject, toolUseId: String): EditSnapshot? {
        val path = DiffPresenter.filePathOf(input) ?: return null
        if (toolUseId.isNotBlank()) get(toolUseId)?.let { return it }
        val file = File(path)
        val existedBefore = file.isFile
        if (existedBefore && file.length() > DiffPresenter.MAX_DIFF_FILE_BYTES) return null
        val beforeText = if (existedBefore) runCatching { file.readText() }.getOrDefault("") else ""
        val snapshot = EditSnapshot(toolName, input, beforeText, path, existedBefore)
        return if (toolUseId.isBlank()) snapshot else remember(toolUseId, snapshot)
    }

    @Synchronized
    private fun remember(toolUseId: String, snapshot: EditSnapshot): EditSnapshot {
        byToolUseId[toolUseId]?.let { return it }
        byToolUseId[toolUseId] = snapshot
        bytes += weight(snapshot)
        val eldest = byToolUseId.entries.iterator()
        while (bytes > maxBytes && byToolUseId.size > 1) {
            bytes -= weight(eldest.next().value)
            eldest.remove()
        }
        return snapshot
    }

    @Synchronized
    fun get(toolUseId: String): EditSnapshot? = byToolUseId[toolUseId]

    @Synchronized
    fun updateInput(toolUseId: String, input: JsonObject) {
        if (toolUseId.isBlank()) return
        byToolUseId.computeIfPresent(toolUseId) { _, snap -> snap.copy(input = input) }
    }

    @Synchronized
    fun clear() {
        byToolUseId.clear()
        bytes = 0
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 32L * 1024 * 1024
        private const val INITIAL_BUCKETS = 16
        private const val LOAD_FACTOR = 0.75f

        private const val ENTRY_OVERHEAD_BYTES = 1024L

        fun weight(snapshot: EditSnapshot): Long =
            ENTRY_OVERHEAD_BYTES + snapshot.beforeText.length.toLong() * Char.SIZE_BYTES
    }
}
