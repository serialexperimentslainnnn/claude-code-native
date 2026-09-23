package dev.lain.claudejb.frontend.window

import dev.lain.claudejb.rpc.FrontendChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class PageMessage(val type: String, val url: String) {

    companion object {
        const val READY = "ready"
        const val OPEN = "open"
        const val PASTE_TEXT = "pasteClipboard"

        private val lenient = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(text: String): PageMessage {
            val obj = runCatching { lenient.parseToJsonElement(text).jsonObject }.getOrNull()
            return PageMessage(obj.field("type"), obj.field("url"))
        }

        fun string(json: String): String? =
            runCatching { lenient.parseToJsonElement(json).jsonPrimitive.contentOrNull }.getOrNull()

        fun quote(text: String): String = JsonPrimitive(text).toString()

        fun imageAttachment(base64: String): String = buildJsonObject {
            put("type", FrontendChannel.ATTACH_IMAGE)
            put("mime", "image/png")
            put("base64", base64)
        }.toString()

        fun isSecureLink(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

        fun isWebLink(url: String): Boolean = isSecureLink(url) || url.trim().startsWith("http://", ignoreCase = true)

        private fun JsonObject?.field(key: String): String = (this?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
