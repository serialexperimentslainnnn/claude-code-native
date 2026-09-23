package dev.lain.claudejb.controller.bridge

import com.intellij.openapi.application.ApplicationManager
import dev.lain.claudejb.controller.context.ProjectTree
import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.model.context.ImageAttachments
import dev.lain.claudejb.util.edt
import dev.lain.claudejb.util.thisLogger
import dev.lain.claudejb.view.payload.chat.JcefTreeData
import dev.lain.claudejb.view.window.ChatPresenter

internal class BridgeAttachments(private val presenter: ChatPresenter) {

    private val log = thisLogger()

    private val tray get() = presenter.tray

    fun handle(m: Msg.Attachments) {
        when (m) {
            is Msg.RemoveAttachment -> tray.remove(m.id)
            Msg.AttachSelection -> tray.addSelection()
            Msg.AttachCurrentFile -> tray.addCurrentFile()
            Msg.RequestAttachData -> tray.pushMenuData()
            is Msg.AttachPath -> attachPaths(listOf(m.path))
            is Msg.TreeChildren -> treeChildren(m)
            is Msg.TreeExpand -> treeExpand(m)
            is Msg.AttachPaths -> attachPaths(m.paths)
            is Msg.Attach -> attachImage(m.name, m.mediaType, m.base64)
            is Msg.AttachImageData -> attachImage(PASTED_NAME + m.mime.substringAfter('/'), m.mime, m.base64)
        }
    }

    private fun attachImage(name: String, mediaType: String, base64: String) {
        val image = ImageAttachments.fromWebPayload(name, mediaType, base64)
        if (image == null) {
            tray.notify(
                "That attachment was not added: only PNG, JPEG, GIF and WebP images are accepted, " +
                    "up to ${ImageAttachments.MAX_IMAGE_BYTES / BYTES_PER_MB} MB.",
            )
            return
        }
        tray.add(image)
    }

    private fun treeMode(wire: String): ProjectTree.Mode? = when (wire) {
        "files" -> ProjectTree.Mode.FILES
        "directories" -> ProjectTree.Mode.DIRECTORIES
        else -> null
    }

    private fun treeChildren(m: Msg.TreeChildren) {
        val mode = treeMode(m.mode) ?: return unknownTreeMode(m.mode)
        presenter.execBuilt("treeChildren") {
            JcefTreeData.childrenJson(m.path, m.mode, ProjectTree.children(presenter.project, m.path, mode)).toString()
        }
    }

    private fun treeExpand(m: Msg.TreeExpand) {
        val mode = treeMode(m.mode) ?: return unknownTreeMode(m.mode)
        presenter.execBuilt("treeExpansion") {
            JcefTreeData.expansionJson(m.path, m.mode, ProjectTree.expand(presenter.project, m.path, mode)).toString()
        }
    }

    private fun attachPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        val root = presenter.project.basePath
        ApplicationManager.getApplication().executeOnPooledThread {
            val wanted = paths.take(ProjectTree.MAX_ENTRIES)
            val files = wanted.mapNotNull { ProjectTree.resolve(root, it)?.path }
            if (files.size != wanted.size) {
                log.warn("Claude Code: ${wanted.size - files.size} of ${wanted.size} attached paths name nothing inside this project")
            }
            if (files.isEmpty()) return@executeOnPooledThread
            edt { tray.addPaths(files) }
        }
    }

    private fun unknownTreeMode(wire: String) =
        log.warn("The attach menu asked to browse the project in a mode this build does not have: $wire")

    private companion object {
        const val BYTES_PER_MB = 1024 * 1024

        const val PASTED_NAME = "clipboard."
    }
}
