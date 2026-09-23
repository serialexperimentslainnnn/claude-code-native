package dev.lain.claudejb.model.context

import java.util.Base64

internal object ImageAttachments {

    private const val MIN_IMAGE_BYTES = 8

    const val MAX_IMAGE_BYTES: Int = 8 * 1024 * 1024

    private const val MAX_BASE64_CHARS: Int = (MAX_IMAGE_BYTES + 2) / 3 * 4 + 4

    private const val MAX_DISPLAY_NAME = 128

    @Suppress("UnusedParameter")
    fun fromWebPayload(name: String, mediaType: String, base64: String): Attachment.Image? {
        val payload = base64.filterNot { it.isWhitespace() }
        if (payload.isEmpty() || payload.length > MAX_BASE64_CHARS) return null
        val bytes = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return null
        if (bytes.size < MIN_IMAGE_BYTES || bytes.size > MAX_IMAGE_BYTES) return null
        val sniffed = sniffMediaType(bytes) ?: return null
        return Attachment.Image(
            displayName = displayNameOf(name, sniffed),
            mediaType = sniffed,
            base64 = Base64.getEncoder().encodeToString(bytes),
        )
    }

    private fun displayNameOf(name: String, mediaType: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .filter { !it.isISOControl() }
            .trim()
            .take(MAX_DISPLAY_NAME)
        return base.ifBlank { "image." + mediaType.substringAfter('/').substringBefore('+') }
    }

    private fun signature(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val PNG_SIGNATURE = signature("89504E470D0A1A0A")
    private val JPEG_SIGNATURE = signature("FFD8FF")
    private val GIF_SIGNATURE = "GIF".toByteArray(Charsets.US_ASCII)

    private val RIFF_SIGNATURE = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP_FORM_TYPE = "WEBP".toByteArray(Charsets.US_ASCII)
    private const val RIFF_FORM_TYPE_OFFSET = 8

    private fun ByteArray.hasSignature(signature: ByteArray, offset: Int = 0): Boolean =
        size >= offset + signature.size && signature.indices.all { this[offset + it] == signature[it] }

    fun sniffMediaType(b: ByteArray): String? = when {
        b.hasSignature(PNG_SIGNATURE) -> "image/png"
        b.hasSignature(JPEG_SIGNATURE) -> "image/jpeg"
        b.hasSignature(GIF_SIGNATURE) -> "image/gif"
        b.hasSignature(RIFF_SIGNATURE) && b.hasSignature(WEBP_FORM_TYPE, RIFF_FORM_TYPE_OFFSET) -> "image/webp"
        else -> null
    }

    fun mediaTypeForExtension(ext: String): String? = when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }
}
