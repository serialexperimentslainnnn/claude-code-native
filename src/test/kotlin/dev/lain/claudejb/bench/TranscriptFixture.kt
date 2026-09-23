package dev.lain.claudejb.bench

import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

object TranscriptFixture {

    fun write(file: Path, targetBytes: Long, seed: Int) {
        val random = Random(seed)
        var written = 0L
        var turn = 0
        Files.newBufferedWriter(file).use { out ->
            while (written < targetBytes) {
                recordsOf(turn++, random).forEach { line ->
                    out.write(line)
                    out.write("\n")
                    written += line.length + 1
                }
            }
        }
    }

    private fun recordsOf(turn: Int, random: Random): List<String> {
        val id = "toolu_bench_$turn"
        val prompt = BenchText.phrase(random, PROMPT_WORDS)
        val reply = BenchText.phrase(random, REPLY_WORDS, LINE_WORDS)
        val output = BenchText.phrase(random, OUTPUT_WORDS, LINE_WORDS)
        val input = "{\"command\":\"ls -la src/module$turn\",\"description\":\"List the module\"}"
        return listOf(
            record("user", "\"$prompt\""),
            record("assistant", "[{\"type\":\"text\",\"text\":\"$reply\"}]"),
            record("assistant", "[{\"type\":\"tool_use\",\"id\":\"$id\",\"name\":\"Bash\",\"input\":$input}]"),
            record("user", "[{\"type\":\"tool_result\",\"tool_use_id\":\"$id\",\"content\":\"$output\"}]"),
        )
    }

    private fun record(type: String, content: String): String =
        "{\"type\":\"$type\",\"timestamp\":\"$STAMP\",\"message\":{\"role\":\"$type\",\"content\":$content}}"

    private const val STAMP = "2026-01-01T00:00:00.000Z"
    private const val PROMPT_WORDS = 40
    private const val REPLY_WORDS = 300
    private const val OUTPUT_WORDS = 200
    private const val LINE_WORDS = 12
}
