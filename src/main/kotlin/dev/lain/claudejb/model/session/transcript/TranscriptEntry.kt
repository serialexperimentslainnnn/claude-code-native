package dev.lain.claudejb.model.session.transcript

enum class Speaker { USER, ASSISTANT, THINKING, TOOL, TOOL_OUTPUT, SYSTEM, ERROR, MEMORY }

enum class ToolState { LOADING, RUNNING, FINISHED, ERROR }

class TranscriptEntry(
    val id: Long,
    val speaker: Speaker,
    text: String,
    val meta: String? = null,
    val toolUseId: String? = null,
    val parentToolUseId: String? = null,
    toolState: ToolState = ToolState.FINISHED,
    val filePath: String? = null,
    val commandText: String? = null,
    val messageText: String? = null,
    val blockedRule: String? = null,
    val bypassedRule: String? = null,
    val bypassAction: String? = null,
    val reviewable: Boolean = false,
) {
    private var settled: String? = text

    private var growing: StringBuilder? = null

    var text: String
        get() = settled ?: growing.toString().also { settled = it }
        internal set(value) {
            settled = value
            growing = null
        }

    internal fun grow(delta: String) {
        val builder = growing ?: StringBuilder(settled.orEmpty()).also { growing = it }
        builder.append(delta)
        settled = null
    }

    var toolState: ToolState = toolState
        internal set

    var elapsedSeconds: Double = 0.0
        internal set

    var toolTitle: String? = null
        internal set

    var places: List<CardPlace> = emptyList()
        internal set

    var trimmed: Boolean = false
        internal set
}
