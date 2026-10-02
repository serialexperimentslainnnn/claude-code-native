package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.ide.bookmark.Bookmark
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface LineBookmarks {
    fun create(file: VirtualFile, line: Int): Bookmark?

    companion object {
        fun of(project: Project): LineBookmarks? = project.serviceOrNull<LineBookmarks>()
    }
}
