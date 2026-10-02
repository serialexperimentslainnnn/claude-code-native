package dev.lain.claudejb.model.mcp

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object BatchRows {

    const val PARALLEL = 4
    const val TRUNCATED = "truncated"
    private const val ERROR = "error"
    private val ZERO = JsonPrimitive(0)
    val FALSE = JsonPrimitive(false)

    suspend fun run(
        items: List<ToolArgs>,
        identity: String,
        parallel: Boolean,
        one: suspend (ToolArgs) -> JsonObject,
    ): List<JsonObject> {
        if (!parallel) return items.map { row(it, identity, one) }
        val gate = Semaphore(PARALLEL)
        return coroutineScope { items.map { async { gate.withPermit { row(it, identity, one) } } }.awaitAll() }
    }

    fun summary(rows: List<JsonObject>, identity: String): JsonObject {
        val clean = rows.indices.filter { clean(rows[it], identity) }.toSet()
        val fold = clean.isNotEmpty() && clean.size >= rows.size - clean.size
        return buildJsonObject {
            put("count", rows.size)
            put("failed", rows.count { it.containsKey(ERROR) })
            if (fold) put("clean", buildJsonArray { clean.sorted().forEach { add(rows[it].getValue(identity)) } })
            put(
                "items",
                buildJsonArray {
                    rows.forEachIndexed { index, row ->
                        when {
                            !fold -> add(row)
                            index !in clean -> add(indexed(index, row))
                        }
                    }
                },
            )
        }
    }

    private fun indexed(index: Int, row: JsonObject): JsonObject = buildJsonObject {
        put("index", index)
        row.forEach { (key, value) -> put(key, value) }
    }

    private fun clean(row: JsonObject, identity: String): Boolean =
        row[identity] is JsonPrimitive &&
            row.values.any { it is JsonArray } &&
            row.all { (key, value) -> key == identity || (key == "count" && value == ZERO) || (value is JsonArray && value.isEmpty()) }

    private suspend fun row(item: ToolArgs, identity: String, one: suspend (ToolArgs) -> JsonObject): JsonObject {
        val id = (item.json[identity] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        val result = runCatching { one(item) }.getOrElse { cause ->
            if (cause !is Exception && cause !is LinkageError) throw cause
            rethrowIfCancelled(cause)
            return failed(identity, id, cause)
        }
        return buildJsonObject {
            if (id != null) put(identity, id)
            result.forEach { (key, value) -> if (kept(key, value, identity, id)) put(key, value) }
        }
    }

    private fun kept(key: String, value: JsonElement, identity: String, id: String?): Boolean =
        (key != identity || id == null) && !(key == TRUNCATED && value == FALSE)

    private fun failed(identity: String, id: String?, cause: Throwable): JsonObject = buildJsonObject {
        put(identity, id ?: "")
        put(ERROR, if (cause is ToolException) cause.message ?: "failed" else "${cause::class.simpleName}: ${cause.message}")
    }
}
