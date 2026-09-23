package dev.lain.claudejb.model.session.agents

import dev.lain.claudejb.model.session.history.SessionTranscriptReader
import dev.lain.claudejb.model.session.transcript.EntryDTO
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

internal class AgentTranscriptTail(private val file: Path) {

    private class Line(val raw: String) {
        val record: JsonObject? by lazy { SessionTranscriptReader.parseRecord(raw) }
    }

    private val window = ArrayDeque<Line>()
    private var trailing: JsonObject? = null
    private var offset = 0L
    private var head = ByteArray(0)
    private var stamp: Pair<Long, Long>? = null
    private var finishedEarlier = false

    var recordCount = 0
        private set

    var entries: List<EntryDTO> = emptyList()
        private set

    var ending: AgentEnding.Ending? = null
        private set

    @Synchronized
    fun refresh() {
        val now = stampOf()
        if (now == stamp) return
        stamp = now
        if (now == null) return reset()
        if (now.first < offset || !sameHead()) reset()
        trailing = null
        readFromOffset()
        val records = window.mapNotNull { it.record } + listOfNotNull(trailing)
        recordCount = accepted + if (trailing?.let(::isConversational) == true) 1 else 0
        entries = SessionTranscriptReader.entriesOf(records, MAX_ENTRIES)
        ending = AgentEnding.of(records, finishedEarlier)
    }

    private var accepted = 0

    private fun reset() {
        window.clear()
        trailing = null
        offset = 0L
        head = ByteArray(0)
        finishedEarlier = false
        accepted = 0
        recordCount = 0
        entries = emptyList()
        ending = null
    }

    private fun stampOf(): Pair<Long, Long>? = runCatching {
        val attrs = Files.readAttributes(file, BasicFileAttributes::class.java)
        attrs.size() to attrs.lastModifiedTime().toMillis()
    }.getOrNull()

    private fun sameHead(): Boolean {
        if (head.isEmpty()) return true
        val now = runCatching { readAt(0, head.size) }.getOrNull() ?: return false
        return now.contentEquals(head)
    }

    private fun readAt(position: Long, length: Int): ByteArray = FileChannel.open(file, StandardOpenOption.READ).use { channel ->
        val buffer = ByteBuffer.allocate(length)
        var read: Int
        do {
            read = channel.read(buffer, position + buffer.position())
        } while (buffer.hasRemaining() && read > 0)
        buffer.array().copyOf(buffer.position())
    }

    private fun readFromOffset() {
        val pending = ByteArrayOutputStream()
        runCatching {
            FileChannel.open(file, StandardOpenOption.READ).use { channel ->
                channel.position(offset)
                val buffer = ByteBuffer.allocate(CHUNK)
                while (channel.read(buffer) > 0) {
                    split(buffer.array(), buffer.position(), pending)
                    buffer.clear()
                }
                offset = channel.position() - pending.size()
            }
        }
        if (head.size < HEAD_BYTES && offset > head.size) head = runCatching { readAt(0, minOf(offset, HEAD_BYTES.toLong()).toInt()) }.getOrDefault(head)
        trailing = if (pending.size() > 0) SessionTranscriptReader.parseRecord(pending.toString(Charsets.UTF_8)) else null
    }

    private fun split(bytes: ByteArray, end: Int, pending: ByteArrayOutputStream) {
        var start = 0
        for (i in 0 until end) {
            if (bytes[i] != NEWLINE) continue
            pending.write(bytes, start, i - start)
            accept(pending.toString(Charsets.UTF_8))
            pending.reset()
            start = i + 1
        }
        pending.write(bytes, start, end - start)
    }

    private fun accept(raw: String) {
        if (raw.isBlank()) return
        val line = Line(raw)
        if (!finishedEarlier && raw.mayEndTurn() && line.record?.let(AgentEnding::endsTurn) == true) finishedEarlier = true
        if (CONVERSATIONAL.any { it in raw }) accepted++
        window.addLast(line)
        if (window.size > MAX_LINES) window.removeFirst()
    }

    private fun isConversational(record: JsonObject): Boolean =
        (record["type"] as? JsonPrimitive)?.contentOrNull in CONVERSATIONAL_TYPES

    private fun String.mayEndTurn(): Boolean = contains(END_TURN) || contains(TOOL_ENDS_TURN)

    private companion object {
        const val MAX_ENTRIES = 500
        const val MAX_LINES = 1_000
        const val CHUNK = 64 * 1024
        const val HEAD_BYTES = 256
        val CONVERSATIONAL_TYPES = setOf("user", "assistant")
        val CONVERSATIONAL = CONVERSATIONAL_TYPES.map { "\"type\":\"$it\"" }
        const val END_TURN = "end_turn"
        const val TOOL_ENDS_TURN = "toolEndsTurn"
        const val NEWLINE = '\n'.code.toByte()
    }
}
