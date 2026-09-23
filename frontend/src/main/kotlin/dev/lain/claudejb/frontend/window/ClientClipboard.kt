package dev.lain.claudejb.frontend.window

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ui.ImageUtil
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

internal object ClientClipboard {

    fun text(): String? =
        runCatching { CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    fun pngBase64(): String? {
        val image = runCatching { CopyPasteManager.getInstance().getContents<Image>(DataFlavor.imageFlavor) }
            .getOrNull() ?: return null
        val out = ByteArrayOutputStream()
        val written = runCatching { ImageIO.write(ImageUtil.toBufferedImage(image), "png", out) }.getOrDefault(false)
        return if (written) Base64.getEncoder().encodeToString(out.toByteArray()) else null
    }

    fun copy(text: String) = CopyPasteManager.getInstance().setContents(StringSelection(text))
}
