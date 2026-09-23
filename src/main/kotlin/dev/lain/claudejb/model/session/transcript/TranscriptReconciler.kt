package dev.lain.claudejb.model.session.transcript

class TranscriptReconciler(private val transcript: TranscriptModel) {

    companion object {
        fun belongsHere(parentToolUseId: String?): Boolean = parentToolUseId == null
    }

    private var liveAssistant: TranscriptEntry? = null
    private var liveThinking: TranscriptEntry? = null

    private var settledThinking: TranscriptEntry? = null

    private fun live(entry: TranscriptEntry?): TranscriptEntry? = entry?.takeUnless { it.trimmed }

    private fun streaming(speaker: Speaker, delta: String): TranscriptEntry =
        transcript.add(speaker, delta, toolState = ToolState.RUNNING)

    fun appendAssistant(delta: String, parentToolUseId: String? = null) {
        if (!belongsHere(parentToolUseId)) return
        live(liveThinking)?.let(transcript::settle)
        liveThinking = null
        val entry = live(liveAssistant)
        if (entry == null) {
            liveAssistant = streaming(Speaker.ASSISTANT, delta)
        } else {
            transcript.append(entry, delta)
        }
    }

    fun finalizeAssistant(full: String, parentToolUseId: String? = null) {
        if (!belongsHere(parentToolUseId)) return
        val entry = live(liveAssistant)
        if (entry != null) transcript.replaceText(entry, full, ToolState.FINISHED) else transcript.add(Speaker.ASSISTANT, full)
        liveAssistant = null
    }

    fun appendThinking(delta: String, parentToolUseId: String? = null) {
        if (!belongsHere(parentToolUseId)) return
        val entry = live(liveThinking)
        if (entry == null) {
            if (delta.isBlank()) return
            liveThinking = streaming(Speaker.THINKING, delta)
            settledThinking = liveThinking
        } else {
            transcript.append(entry, delta)
        }
    }

    fun finalizeThinking(full: String, parentToolUseId: String? = null) {
        if (!belongsHere(parentToolUseId)) return
        val entry = live(liveThinking) ?: live(settledThinking)
        when {
            full.isBlank() -> entry?.let(transcript::settle)
            entry != null -> transcript.replaceText(entry, full, ToolState.FINISHED)
            else -> transcript.add(Speaker.THINKING, full)
        }
        liveThinking = null
        settledThinking = null
    }

    fun onMessageBoundary() {
        listOfNotNull(live(liveAssistant), live(liveThinking), live(settledThinking)).forEach(transcript::settle)
        liveAssistant = null
        liveThinking = null
        settledThinking = null
    }
}
