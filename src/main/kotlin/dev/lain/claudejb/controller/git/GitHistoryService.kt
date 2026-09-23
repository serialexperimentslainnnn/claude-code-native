package dev.lain.claudejb.controller.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import dev.lain.claudejb.model.git.GitBranchTopology
import dev.lain.claudejb.model.git.GitCommitInfo
import dev.lain.claudejb.model.git.GitLogScope
import dev.lain.claudejb.model.git.GitRefInfo
import dev.lain.claudejb.util.logger

@Service(Service.Level.PROJECT)
class GitHistoryService(private val project: Project) {

    fun repositoryRoots(): List<String> = readGit(emptyList()) { git -> git.repositoryRoots(project).map { it.path } }

    fun isAvailable(): Boolean = repositoryRoots().isNotEmpty()

    fun primaryRepositoryRoot(): String? {
        val roots = repositoryRoots()
        val base = project.basePath ?: return roots.firstOrNull()
        return roots.filter { base == it || base.startsWith("$it/") }.maxByOrNull { it.length } ?: roots.firstOrNull()
    }

    fun currentBranch(): String? = withPrimaryRoot { git, root -> git.currentBranchName(project, root) }

    fun headRevision(): String? = withPrimaryRoot { git, root -> git.currentRevision(project, root) }

    fun recentCommits(
        limit: Int = DEFAULT_COMMIT_LIMIT,
        scope: GitLogScope = GitLogScope.CURRENT_BRANCH,
    ): List<GitCommitInfo> {
        if (limit <= 0) return emptyList()
        if (refusedOnEdt("recentCommits()", "git log")) return emptyList()
        return withPrimaryRoot(emptyList()) { git, root -> git.recentCommits(project, root, limit, scope) }
    }

    fun commit(hash: String): GitCommitInfo? {
        if (refusedOnEdt("commit()", "git log -n 1")) return null
        return withPrimaryRoot<GitCommitInfo?>(null) { git, root -> git.commit(project, root, hash) }
    }

    fun fileHistory(relativePath: String, limit: Int): List<GitCommitInfo> {
        if (limit <= 0 || refusedOnEdt("fileHistory()", "git log --follow")) return emptyList()
        return withPrimaryRoot(emptyList()) { git, root -> git.fileHistory(project, root, relativePath, limit) }
    }

    fun branchTopology(): GitBranchTopology {
        if (refusedOnEdt("branchTopology()", "git rev-list / git merge-base")) return GitBranchTopology.NONE
        return withPrimaryRoot(GitBranchTopology.NONE) { git, root -> git.branchTopology(project, root) }
    }

    fun refs(): List<GitRefInfo> = withPrimaryRoot(emptyList()) { git, root -> git.refs(project, root) }

    fun workingTreeChanges(): List<String> {
        val root = primaryRepositoryRoot() ?: return emptyList()
        return ChangeListManager.getInstance(project).allChanges
            .mapNotNull { change -> (change.afterRevision ?: change.beforeRevision)?.file?.path }
            .map { path -> GitCommitInfo.relativize(root, path) }
            .distinct()
            .sorted()
    }

    fun hasConflicts(): Boolean {
        if (ChangeListManager.getInstance(project).allChanges.any { it.fileStatus == FileStatus.MERGED_WITH_CONFLICTS }) {
            return true
        }
        return withPrimaryRoot(false) { git, root -> git.midOperation(project, root) }
    }

    fun onRepositoryChanged(parent: Disposable, onChanged: () -> Unit) {
        val git = gitAccess() ?: return
        runCatching { git.onRepositoryChanged(project, parent, onChanged) }
            .onFailure { LOG.warn("Could not subscribe to Git repository changes for ${project.name}", it) }
    }

    private fun refusedOnEdt(caller: String, commands: String): Boolean {
        if (!ApplicationManager.getApplication().isDispatchThread) return false
        LOG.warn("$caller was called on the EDT; refusing to run `$commands` there. Move the call off the EDT.")
        return true
    }

    private fun <T> withPrimaryRoot(fallback: T, block: (GitAccess, VirtualFile) -> T): T =
        readGit(fallback) { git ->
            val wanted = primaryRepositoryRoot() ?: return@readGit fallback
            val root = git.repositoryRoots(project).firstOrNull { it.path == wanted } ?: return@readGit fallback
            block(git, root)
        }

    private fun withPrimaryRoot(block: (GitAccess, VirtualFile) -> String?): String? = withPrimaryRoot<String?>(null, block)

    private fun <T> readGit(fallback: T, block: (GitAccess) -> T): T {
        val git = gitAccess() ?: return fallback
        return try {
            block(git)
        } catch (e: VcsException) {
            LOG.warn("Git query failed for ${project.name}", e)
            fallback
        } catch (e: LinkageError) {
            LOG.warn("The Git module no longer links against this IDE's Git4Idea; the Git surface stays off", e)
            fallback
        }
    }

    private fun gitAccess(): GitAccess? = if (GitAvailability.isGitPluginEnabled()) serviceOrNull<GitAccess>() else null

    companion object {

        const val DEFAULT_COMMIT_LIMIT = 20

        private val LOG = logger<GitHistoryService>()
    }
}
