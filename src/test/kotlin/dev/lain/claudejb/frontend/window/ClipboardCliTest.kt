package dev.lain.claudejb.frontend.window

import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.model.bridge.Msg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClipboardCliTest {

    @Test
    fun `plain utf-8 text wins over html and file lists`() {
        val types = listOf("text/html", "text/uri-list", "TEXT", "text/plain;charset=utf-8")

        assertEquals("text/plain;charset=utf-8", ClipboardCli.preferredTextType(types))
        assertEquals("UTF8_STRING", ClipboardCli.preferredTextType(listOf("STRING", "UTF8_STRING")))
        assertNull(ClipboardCli.preferredTextType(listOf("text/html", "text/uri-list", "image/png")))
    }

    @Test
    fun `png is read before any other image the clipboard offers`() {
        assertEquals("image/png", ClipboardCli.imageType(listOf("image/bmp", "image/jpeg", "image/png")))
        assertEquals("image/jpeg", ClipboardCli.imageType(listOf("text/plain", "image/jpeg")))
        assertNull(ClipboardCli.imageType(listOf("text/plain")))
    }

    @Test
    fun `a copied file is attached only when it is an image the host accepts`() {
        assertEquals("image/jpeg", ClipboardCli.mimeOfPath("/tmp/shot.JPG"))
        assertEquals("image/webp", ClipboardCli.mimeOfPath("/tmp/shot.webp"))
        assertNull(ClipboardCli.mimeOfPath("/tmp/notes.txt"))
    }

    @Test
    fun `an empty clipboard reaches the host as a notice it knows how to word`() {
        val parsed = JcefBridge.parse(PageMessage.clipboardEmpty(image = true, help = "install xclip"))

        assertEquals(Msg.ClipboardEmpty(image = true, help = "install xclip"), parsed)
        assertEquals(Msg.ClipboardEmpty(image = false, help = ""), JcefBridge.parse(PageMessage.clipboardEmpty(false, null)))
    }

    @Test
    fun `the image menu asks the client to say so when nothing is found`() {
        assertEquals(true, PageMessage.parse("""{"type":"pasteClipboardImage","notify":true}""").notify)
        assertEquals(false, PageMessage.parse("""{"type":"pasteClipboard"}""").notify)
    }
}
