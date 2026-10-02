package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class ProviderLineBookmarks(private val project: Project) : LineBookmarks {
    override fun create(file: VirtualFile, line: Int): Bookmark? =
        LineBookmarkProvider.Util.find(project)?.createBookmark(file, line)
}
