package dev.lain.claudejb.bench

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

object BenchReport {

    private val RESULTS: Path = Path.of("build", "bench", "results.txt")

    fun record(name: String, value: Double, unit: String) =
        emit("BENCH $name ${String.format(Locale.ROOT, "%.2f", value)} $unit")

    fun record(name: String, value: Long, unit: String) = emit("BENCH $name $value $unit")

    @Synchronized
    private fun emit(line: String) {
        println(line)
        Files.createDirectories(RESULTS.parent)
        Files.writeString(RESULTS, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
}
