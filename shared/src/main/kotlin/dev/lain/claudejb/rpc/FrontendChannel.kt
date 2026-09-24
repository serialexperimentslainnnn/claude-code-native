package dev.lain.claudejb.rpc

object FrontendChannel {
    const val PREFIX = "frontend."
    const val COPY = "frontend.copy"
    const val BROWSE = "frontend.browse"
    const val VIBE = "frontend.vibe"
    const val FOCUS = "frontend.focus"
    const val TERMINAL = "frontend.terminal"
    const val ACTION = "frontend.action"
    const val ATTACH_IMAGE = "attachImageData"
    const val CLIPBOARD_EMPTY = "clipboardEmpty"

    val clientMessages: Set<String> = setOf("pasteClipboard", "pasteClipboardImage")

    val SNAPSHOT_METHODS: Set<String> = setOf(
        "meta", "state", "session", "settingsMenu", "permissions", "tabs", "mcp", "guard", "log",
        "authState", "attachments", "attachData", "gitChat", "vulnInventory", "theme", VIBE,
    )

    fun isFrontendPush(method: String): Boolean = method.startsWith(PREFIX)
}
