package dev.lain.claudejb.model.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ToolArgsTest {

    private fun args(json: String) = ToolArgs(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `a JSON null is an absent argument, never the text null`() {
        val a = args("""{"path":null,"max":null,"regex":null,"paths":null}""")
        assertNull(a.optionalString("path"))
        assertEquals(7, a.int("max", 7))
        assertEquals(false, a.boolean("regex", false))
        assertEquals(emptyList<String>(), a.strings("paths"))
        assertThrows<ToolException> { a.string("path") }
        assertNull(Batch.expand(a, Batch.PATHS))
    }

    @Test
    fun `a null singular beside its plural is no clash`() {
        val items = Batch.expand(args("""{"path":null,"paths":["a.kt","b.kt"]}"""), Batch.PATHS)!!
        assertEquals(listOf("a.kt", "b.kt"), items.map { it.string("path") })
    }

    @Test
    fun `max falls back to its default and never passes its ceiling`() {
        assertEquals(50, args("{}").max(50, 500))
        assertEquals(500, args("""{"max":100000}""").max(50, 500))
        assertEquals(1, args("""{"max":0}""").max(50, 500))
        assertEquals(20, args("""{"max":"20"}""").max(50, 500))
    }
}
