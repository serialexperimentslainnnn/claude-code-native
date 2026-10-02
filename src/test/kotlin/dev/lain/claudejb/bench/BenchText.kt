package dev.lain.claudejb.bench

import kotlin.random.Random

object BenchText {

    private val WORDS = listOf(
        "session", "restore", "agent", "transcript", "entry", "record",
        "parse", "tool", "output", "line", "file", "project",
    )

    fun word(random: Random): String = WORDS[random.nextInt(WORDS.size)]

    fun phrase(random: Random, words: Int, breakEvery: Int = Int.MAX_VALUE): String = buildString {
        repeat(words) { index ->
            if (index > 0) append(if (index % breakEvery == 0) "\\n" else " ")
            append(word(random))
        }
    }
}
