package dev.lain.claudejb.frontend.window

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ui.ImageUtil
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal object ClientClipboard {

    val preferHost: Boolean by lazy {
        runCatching { Toolkit.getDefaultToolkit().javaClass.name == WAYLAND_TOOLKIT }.getOrDefault(false)
    }

    fun image(): ClipImage? = awtImage() ?: ClipboardCli.image()

    fun text(): String? =
        runCatching { CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: ClipboardCli.text()

    fun hasText(): Boolean {
        val awt = runCatching { CopyPasteManager.getInstance().areDataFlavorsAvailable(DataFlavor.stringFlavor) }
            .getOrDefault(false)
        return awt || ClipboardCli.textType() != null
    }

    fun imageHelp(): String? {
        if (!ClipboardCli.isLinux()) return null
        if (ClipboardCli.findExecutable("wl-paste") != null || ClipboardCli.findExecutable("xclip") != null) return null
        return "image paste needs 'wl-clipboard' (Wayland) or 'xclip' (X11): " + ClipboardCli.installHint()
    }

    fun copy(text: String) = CopyPasteManager.getInstance().setContents(StringSelection(text))

    private fun awtImage(): ClipImage? {
        val image = runCatching { CopyPasteManager.getInstance().getContents<Image>(DataFlavor.imageFlavor) }
            .getOrNull() ?: return null
        val out = ByteArrayOutputStream()
        val written = runCatching { ImageIO.write(ImageUtil.toBufferedImage(image), "png", out) }.getOrDefault(false)
        return if (written) ClipImage("image/png", out.toByteArray()) else null
    }

    private const val WAYLAND_TOOLKIT = "sun.awt.wl.WLToolkit"
}
