package dev.lain.claudejb.rpc

object FrontendChannel {
    const val PREFIX = "frontend."
    const val COPY = "frontend.copy"
    const val BROWSE = "frontend.browse"
    const val VIBE = "frontend.vibe"
    const val FOCUS = "frontend.focus"
    const val ACTION = "frontend.action"
    const val ATTACH_IMAGE = "attachImageData"

    val clientMessages: Set<String> = setOf("pasteClipboard", "pasteClipboardImage")

    fun isFrontendPush(method: String): Boolean = method.startsWith(PREFIX)
}
