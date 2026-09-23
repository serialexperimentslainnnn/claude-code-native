package dev.lain.claudejb.controller.mcp.tools.vcs

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import dev.lain.claudejb.model.mcp.ToolException

interface GitWorkingCopy {

    fun stage(paths: List<FilePath>)

    fun unstage(paths: List<FilePath>)

    fun commit(message: String, paths: List<FilePath>, amend: Boolean)

    fun createBranch(name: String, startPoint: String)

    fun checkout(reference: String, newBranch: String?)

    fun fetch(remoteName: String?): String

    fun pull(remoteName: String?, branch: String): String

    fun push(remoteName: String?, branch: String): String
}

interface GitRepositoryOps {

    fun stash(action: String, message: String?): List<String>

    fun worktrees(action: String, path: String?, branch: String?): List<String>

    fun remotes(action: String, name: String?, url: String?): List<String>

    fun show(reference: String, path: String): List<String>

    fun branchOp(action: String, reference: String, target: String?)
}

interface GitWrites : GitWorkingCopy, GitRepositoryOps {

    companion object {
        const val MISSING = "the Git plugin (Git4Idea) is disabled in this IDE"

        const val BRANCH_ACTIONS =
            "merge, rebase, rebase_onto, compare, diff_with_local, rename, delete, checkout, checkout_as_new or new_tag"

        fun of(project: Project): GitWrites = project.serviceOrNull<GitWrites>() ?: throw ToolException(MISSING)
    }
}
