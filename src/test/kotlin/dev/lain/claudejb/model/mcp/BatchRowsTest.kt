package dev.lain.claudejb.model.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class BatchRowsTest {

    private fun args(json: String, parallel: Boolean = false) = ToolArgs(Json.parseToJsonElement(json).jsonObject, "tu_1", parallel)

    private fun items(out: JsonObject): List<JsonObject> = out["items"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun `any failure of one item is that item's error, and the items after it still run`() = runBlocking {
        val applied = mutableListOf<String>()
        val out = Batch.run(args("""{"paths":["a.kt","boom.kt","pce.kt","c.kt"]}"""), Batch.PATHS) { item ->
            val path = item.string("path")
            if (path == "boom.kt") throw IllegalStateException("no document")
            if (path == "pce.kt") throw CancellationException("a read action was interrupted")
            applied += path
            buildJsonObject { put("text", path) }
        }
        assertEquals(listOf("a.kt", "c.kt"), applied)
        assertEquals(2, out["failed"]!!.jsonPrimitive.content.toInt())
        val rows = items(out)
        assertEquals("IllegalStateException: no document", rows[1]["error"]!!.jsonPrimitive.content)
        assertTrue("interrupted" in rows[2]["error"]!!.jsonPrimitive.content)
        assertEquals("c.kt", rows[3]["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `read-only items run a few at a time and come back in request order`() = runBlocking {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val paths = (1..10).joinToString(",") { "\"f$it.kt\"" }
        val out = Batch.run(args("""{"paths":[$paths]}""", parallel = true), Batch.PATHS) { item ->
            peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            delay(20.milliseconds)
            running.decrementAndGet()
            buildJsonObject { put("text", item.string("path")) }
        }
        assertEquals((1..10).map { "f$it.kt" }, items(out).map { it["path"]!!.jsonPrimitive.content })
        assertTrue(peak.get() in 2..BatchRows.PARALLEL) { "peak ${peak.get()}" }
    }

    @Test
    fun `clean items fold into one line and the rest keep their request index`() = runBlocking {
        val out = Batch.run(args("""{"paths":["a.kt","b.kt","c.kt"]}"""), Batch.PATHS) { item ->
            val dirty = item.string("path") == "b.kt"
            buildJsonObject {
                put("count", if (dirty) 1 else 0)
                put("truncated", false)
                put("problems", if (dirty) buildJsonArray { add(buildJsonObject { put("line", 3) }) } else JsonArray(emptyList()))
            }
        }
        assertEquals(listOf("a.kt", "c.kt"), out["clean"]!!.jsonArray.map { it.jsonPrimitive.content })
        val row = items(out).single()
        assertEquals(1, row["index"]!!.jsonPrimitive.content.toInt())
        assertEquals("b.kt", row["path"]!!.jsonPrimitive.content)
        assertNull(row["truncated"])
    }

    @Test
    fun `a batch with few clean items stays positional`() = runBlocking {
        val out = Batch.run(args("""{"paths":["a.kt","b.kt","c.kt"]}"""), Batch.PATHS) { item ->
            val clean = item.string("path") == "a.kt"
            buildJsonObject { put("matches", if (clean) JsonArray(emptyList()) else buildJsonArray { add(buildJsonObject { put("line", 1) }) }) }
        }
        assertNull(out["clean"])
        assertEquals(listOf("a.kt", "b.kt", "c.kt"), items(out).map { it["path"]!!.jsonPrimitive.content })
        assertNull(items(out)[0]["index"])
    }

    @Test
    fun `a truncated flag that is false is left out of a single answer`() {
        val answer = buildJsonObject {
            put("truncated", false)
            put("count", 1)
        }
        val text = ToolResult.toon(answer).text
        assertEquals("count: 1", text)
        assertTrue("truncated: true" in ToolResult.toon(buildJsonObject { put("truncated", true) }).text)
    }
}
