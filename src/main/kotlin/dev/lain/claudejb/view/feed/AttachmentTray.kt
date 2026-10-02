package dev.lain.claudejb.view.feed

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.context.EditorContextProvider
import dev.lain.claudejb.controller.context.FilePickerHelper
import dev.lain.claudejb.model.context.Attachment
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.util.PluginIdentity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class AttachmentTray(
    private val project: Project,
    private val emit: (PagePush) -> Unit,
    private val focusInput: () -> Unit,
) {

    private val pending = LinkedHashMap<String, Attachment>()
    private var nextId = 0L

    fun all(): List<Attachment> = pending.values.toList()

    fun take(): List<Attachment> {
        val taken = all()
        pending.clear()
        push()
        return taken
    }

    fun add(attachment: Attachment) {
        pin(attachment)
        push()
        focusInput()
    }

    private fun pin(attachment: Attachment) {
        pending["a" + (nextId++)] = attachment
    }

    fun remove(id: String) {
        pending.remove(id)
        push()
    }

    fun addCurrentFile() {
        val path = EditorContextProvider.currentFilePath(project) ?: return
        addPath(path)
    }

    fun addPath(path: String) = addPaths(listOf(path))

    fun addPaths(paths: List<String>) {
        val known = pending.values.filterIsInstance<Attachment.FileRef>().mapTo(HashSet()) { it.path }
        var pinned = 0
        for (path in paths) {
            if (path.isBlank() || !known.add(path)) continue
            pin(Attachment.FileRef(path, FilePickerHelper.displayName(project, path)))
            pinned++
        }
        if (pinned == 0) return
        push()
        focusInput()
    }

    fun addSelection() = EditorContextProvider.selectionAsAttachment(project)?.let { add(it) }

    fun push() = emit(PagePush("attachments", json()))

    fun pushMenuData() {
        val recent = FilePickerHelper.recentFiles(project, RECENT_FILES_LIMIT).map { path ->
            buildJsonObject {
                put("path", path)
                put("name", FilePickerHelper.displayName(project, path))
                put("ext", path.substringAfterLast('.', "").lowercase())
            }
        }
        val payload = buildJsonObject {
            put("recent", JsonArray(recent))
            put("hasSelection", EditorContextProvider.currentSelection(project) != null)
            put("hasFile", EditorContextProvider.currentFilePath(project) != null)
        }
        emit(PagePush("attachData", payload.toString()))
    }

    fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(PluginIdentity.NOTIFICATION_GROUP)
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun json(): String = JsonArray(
        pending.map { (id, a) ->
            buildJsonObject {
                put("id", id)
                put("label", a.displayName)
                put(
                    "kind",
                    when (a) {
                        is Attachment.Image -> "image"
                        is Attachment.Selection -> "selection"
                        is Attachment.FileRef -> "file"
                    },
                )
            }
        },
    ).toString()

    private companion object {
        const val RECENT_FILES_LIMIT = 14
    }
}
