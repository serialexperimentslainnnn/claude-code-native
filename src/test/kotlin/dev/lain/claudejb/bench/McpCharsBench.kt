package dev.lain.claudejb.bench

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.mcp.IdeMcpService
import dev.lain.claudejb.mcp.McpClient
import dev.lain.claudejb.model.session.launch.IdeServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.nio.file.Files
import java.nio.file.Path

class McpCharsBench : BasePlatformTestCase() {

    private lateinit var dir: Path

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        dir = Files.createDirectories(Path.of(project.basePath!!).resolve(DIR))
        FILES.forEachIndexed { index, name ->
            val file = dir.resolve(name)
            Files.writeString(file, source(index))
            assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file))
        }
    }

    override fun tearDown() {
        try {
            dir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun `test the characters the fixed calls return`() {
        val paths = FILES.map { dir.resolve(it).toString() }
        connect(IdeServer.CODE).use { code ->
            record("mcp_chars_read_file", paths.sumOf { chars(code, "read_file", buildJsonObject { put("path", it) }) })
            record(
                "mcp_chars_search_text",
                chars(
                    code,
                    "search_text",
                    buildJsonObject {
                        put("query", QUERY)
                        put("path", DIR)
                    },
                ),
            )
            record("mcp_chars_project_problems", chars(code, "project_problems", buildJsonObject {}))
            record(
                "mcp_chars_batch",
                chars(code, "read_file", buildJsonObject { putJsonArray("paths") { paths.forEach { add(it) } } }),
            )
        }
        connect(IdeServer.OPS).use { ops ->
            record("mcp_chars_services", chars(ops, "services", buildJsonObject {}))
        }
    }

    private fun connect(server: IdeServer): McpClient {
        val service = IdeMcpService.getInstance(project)
        service.expectConnections(1)
        val client = McpClient.connect(Path.of(service.sockets().getValue(server)))
        client.initialize()
        return client
    }

    private fun chars(client: McpClient, tool: String, args: JsonObject): Long =
        runCatching { client.run(tool, args) }.getOrElse { it.message.orEmpty() }.length.toLong()

    private fun record(name: String, chars: Long) = BenchReport.record(name, chars, "chars")

    private fun source(index: Int): String = buildString {
        appendLine("package bench")
        appendLine()
        repeat(FUNCTIONS) { appendLine("fun task${index}_$it(value: Int): Int = value * ${it + 1}") }
    }

    private companion object {
        const val DIR = "benchsrc"
        const val QUERY = "fun task1_"
        const val FUNCTIONS = 40
        val FILES = listOf("Alpha.kt", "Beta.kt", "Gamma.kt")
    }
}
