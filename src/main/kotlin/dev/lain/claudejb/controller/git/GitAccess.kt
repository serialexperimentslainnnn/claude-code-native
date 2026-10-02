package dev.lain.claudejb.controller.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.lain.claudejb.model.git.GitBranchTopology
import dev.lain.claudejb.model.git.GitCommitInfo
import dev.lain.claudejb.model.git.GitLogScope
import dev.lain.claudejb.model.git.GitRefInfo

interface GitAccess {

    fun repositoryRoots(project: Project): List<VirtualFile>

    fun currentBranchName(project: Project, root: VirtualFile): String?

    fun currentRevision(project: Project, root: VirtualFile): String?

    fun refs(project: Project, root: VirtualFile): List<GitRefInfo>

    fun recentCommits(project: Project, root: VirtualFile, limit: Int, scope: GitLogScope): List<GitCommitInfo>

    fun commit(project: Project, root: VirtualFile, hash: String): GitCommitInfo?

    fun fileHistory(project: Project, root: VirtualFile, relativePath: String, limit: Int): List<GitCommitInfo>

    fun branchTopology(project: Project, root: VirtualFile): GitBranchTopology

    fun onRepositoryChanged(project: Project, parent: Disposable, onChanged: () -> Unit)

    fun midOperation(project: Project, root: VirtualFile): Boolean
}
