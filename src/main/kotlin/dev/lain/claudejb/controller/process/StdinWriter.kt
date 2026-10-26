package dev.lain.claudejb.controller.process

import dev.lain.claudejb.util.thisLogger
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

class StdinWriter(private val executor: Executor) {
    private val log = thisLogger()

    fun enqueue(stream: OutputStream, line: String) {
        val bytes = "$line\n".toByteArray(StandardCharsets.UTF_8)
        executor.execute {
            runCatching {
                stream.write(bytes)
                stream.flush()
            }.onFailure { log.warn("Could not write to claude stdin; the process is gone or its pipe is closed", it) }
        }
    }
}
