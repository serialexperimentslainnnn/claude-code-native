package dev.lain.claudejb.model.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class ImageAttachmentsTest {

    @Test
    fun `mediaTypeForExtension maps image extensions`() {
        assertEquals("image/png", ImageAttachments.mediaTypeForExtension("png"))
        assertEquals("image/jpeg", ImageAttachments.mediaTypeForExtension("jpg"))
        assertEquals("image/jpeg", ImageAttachments.mediaTypeForExtension("jpeg"))
        assertEquals("image/gif", ImageAttachments.mediaTypeForExtension("gif"))
        assertEquals("image/webp", ImageAttachments.mediaTypeForExtension("webp"))
    }

    @Test
    fun `mediaTypeForExtension returns null for non-images`() {
        assertNull(ImageAttachments.mediaTypeForExtension("txt"))
        assertNull(ImageAttachments.mediaTypeForExtension(""))
    }

    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00) + ByteArray(8)
    private val gifMagic = "GIF89a".toByteArray() + ByteArray(4)

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `sniffMediaType reads the format out of the leading bytes`() {
        assertEquals("image/png", ImageAttachments.sniffMediaType(pngMagic))
        assertEquals("image/jpeg", ImageAttachments.sniffMediaType(jpegMagic))
        assertEquals("image/gif", ImageAttachments.sniffMediaType(gifMagic))
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()
        assertEquals("image/webp", ImageAttachments.sniffMediaType(webp))
        assertNull(ImageAttachments.sniffMediaType("not an image at all".toByteArray()))
    }

    @Test
    fun `fromWebPayload accepts a real image and re-encodes exactly the validated bytes`() {
        val img = ImageAttachments.fromWebPayload("shot.png", "image/png", b64(pngMagic))
        requireNotNull(img) { "a genuine PNG must be accepted" }
        assertEquals("image/png", img.mediaType)
        assertEquals("shot.png", img.displayName)
        assertTrue(Base64.getDecoder().decode(img.base64).contentEquals(pngMagic))
    }

    @Test
    fun `fromWebPayload ignores the declared media type and trusts the bytes`() {
        val img = ImageAttachments.fromWebPayload("shot.png", "image/png", b64(jpegMagic))
        requireNotNull(img) { "the payload is a real JPEG, so it is accepted" }
        assertEquals("image/jpeg", img.mediaType)
    }

    @Test
    fun `fromWebPayload rejects a non-image announced as an image`() {
        val executable = byteArrayOf(0x4D, 0x5A) + ByteArray(32)
        assertNull(ImageAttachments.fromWebPayload("payload.png", "image/png", b64(executable)))
    }

    @Test
    fun `fromWebPayload rejects SVG, which is markup and not a supported image block`() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".toByteArray()
        assertNull(ImageAttachments.fromWebPayload("x.svg", "image/svg+xml", b64(svg)))
    }

    @Test
    fun `fromWebPayload enforces the size cap`() {
        val oversized = pngMagic + ByteArray(ImageAttachments.MAX_IMAGE_BYTES) { 0x00 }
        assertNull(ImageAttachments.fromWebPayload("huge.png", "image/png", b64(oversized)))
        val atLimit = pngMagic + ByteArray(ImageAttachments.MAX_IMAGE_BYTES - pngMagic.size) { 0x00 }
        assertNotNull(ImageAttachments.fromWebPayload("big.png", "image/png", b64(atLimit)))
    }

    @Test
    fun `fromWebPayload rejects an empty or undecodable payload`() {
        assertNull(ImageAttachments.fromWebPayload("x.png", "image/png", ""))
        assertNull(ImageAttachments.fromWebPayload("x.png", "image/png", "!!!not base64!!!"))
        assertNull(ImageAttachments.fromWebPayload("x.png", "image/png", b64(byteArrayOf(0x89.toByte(), 0x50))))
    }

    @Test
    fun `fromWebPayload tolerates whitespace in the base64 payload`() {
        val wrapped = b64(pngMagic).chunked(4).joinToString("\n")
        assertNotNull(ImageAttachments.fromWebPayload("shot.png", "image/png", wrapped))
    }

    @Test
    fun `fromWebPayload reduces the display name to a safe basename`() {
        assertEquals(
            "shot.png",
            ImageAttachments.fromWebPayload("/etc/../home/u/shot.png", "image/png", b64(pngMagic))?.displayName,
        )
        assertEquals(
            "shot.png",
            ImageAttachments.fromWebPayload("C:\\Users\\u\\shot.png", "image/png", b64(pngMagic))?.displayName,
        )
        assertEquals(
            "ab.png",
            ImageAttachments.fromWebPayload("a\u0000b\n.png", "image/png", b64(pngMagic))?.displayName,
        )
        assertEquals("image.png", ImageAttachments.fromWebPayload("", "image/png", b64(pngMagic))?.displayName)
        val long = ImageAttachments.fromWebPayload("x".repeat(5000) + ".png", "image/png", b64(pngMagic))
        assertTrue((long?.displayName?.length ?: 0) <= 128, "the display name must be bounded")
    }
}
