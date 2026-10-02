package dev.lain.claudejb.model.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<Param> = emptyList(),
    val mutates: Boolean = false,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val parallel: Boolean = !mutates,
) {
    val inputSchema: JsonObject
        get() = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put(
                "properties",
                buildJsonObject {
                    for (param in params) put(param.name, property(param))
                },
            )
            put("required", buildJsonArray { params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) } })
        }

    private fun property(param: Param): JsonObject = buildJsonObject {
        put("type", param.type)
        put("description", param.description)
        param.items?.let { put("items", itemSchema(it)) }
    }

    private fun itemSchema(items: Items): JsonObject = buildJsonObject {
        put("type", items.type)
        if (items.params.isNotEmpty()) {
            put("additionalProperties", false)
            put("properties", buildJsonObject { for (param in items.params) put(param.name, property(param)) })
            put("required", buildJsonArray { items.params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) } })
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    }
}

data class Items(val type: String, val params: List<Param> = emptyList())

data class Param(
    val name: String,
    val description: String,
    val type: String = "string",
    val required: Boolean = true,
    val items: Items? = null,
) {
    companion object {
        const val MAX_CEILING = 500

        fun max(what: String, default: Int, ceiling: Int = MAX_CEILING): Param =
            Param("max", "Maximum $what to return (default $default, at most $ceiling)", type = "integer", required = false)
    }
}

class Tool(val spec: ToolSpec, val run: suspend (ToolArgs) -> ToolResult)

class ToolDomain(val name: String, val description: String, val tools: List<Tool>) {
    init {
        require(tools.size <= MAX_TOOLS) { "domain $name exposes ${tools.size} tools; the ceiling is $MAX_TOOLS" }
        tools.map { it.spec }.forEach { spec ->
            require(spec.name in Batch.ONE_CALL_LISTS || spec.params.none { it.type == "array" && it.items == null }) {
                "${spec.name} takes a list that is not a batch; name it in Batch.ONE_CALL_LISTS so the host draws one card"
            }
        }
    }

    companion object {
        const val MAX_TOOLS = 4
    }
}
