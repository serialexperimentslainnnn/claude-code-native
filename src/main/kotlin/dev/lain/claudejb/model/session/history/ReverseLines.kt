package dev.lain.claudejb.model.session.history

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object ReverseLines {

    private const val BLOCK = 64 * 1024

    fun <T> read(path: Path, block: (Sequence<String>) -> T): T =
        FileChannel.open(path, StandardOpenOption.READ).use { channel -> block(lines(channel)) }

    private fun lines(channel: FileChannel): Sequence<String> = sequence {
        val buffer = ByteBuffer.allocate(BLOCK)
        val partial = ArrayDeque<ByteArray>()
        var position = channel.size()
        while (position > 0) {
            val length = minOf(BLOCK.toLong(), position).toInt()
            position -= length
            buffer.clear().limit(length)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer, position + buffer.position()) < 0) break
            }
            val bytes = buffer.array()
            var end = buffer.position()
            for (i in end - 1 downTo 0) {
                if (bytes[i] != NEWLINE) continue
                yield(join(bytes, i + 1, end, partial))
                partial.clear()
                end = i
            }
            if (end > 0) partial.addFirst(bytes.copyOfRange(0, end))
        }
        if (partial.isNotEmpty()) yield(join(ByteArray(0), 0, 0, partial))
    }

    private fun join(bytes: ByteArray, from: Int, to: Int, rest: ArrayDeque<ByteArray>): String {
        if (rest.isEmpty()) return String(bytes, from, to - from, Charsets.UTF_8)
        val out = ByteArrayOutputStream(to - from + rest.sumOf { it.size })
        out.write(bytes, from, to - from)
        rest.forEach { out.write(it) }
        return out.toString(Charsets.UTF_8)
    }

    private const val NEWLINE = '\n'.code.toByte()
}
