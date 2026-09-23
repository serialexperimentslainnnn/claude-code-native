package dev.lain.claudejb.controller.mcp.tools.vcs

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.github.GitHubAvailability
import dev.lain.claudejb.model.mcp.ToolException

interface GitHubAccess {

    class Repository(val server: String, val owner: String, val name: String)

    class Head(val number: Long, val title: String, val state: String, val draft: Boolean, val author: String, val url: String)

    class Request(val head: Head, val updatedAt: String, val branches: Branches? = null)

    class Branches(val base: String, val head: String, val body: String, val reviewDecision: String, val headSha: String = "")

    class Check(val name: String, val state: String, val required: Boolean, val url: String)

    class Mergeability(val mergeable: String, val mergeState: String, val canMerge: Boolean, val headSha: String, val checks: List<Check>)

    fun repository(): Repository

    suspend fun pullRequests(state: String, max: Int): List<Request>

    suspend fun pullRequest(number: Long): Request

    suspend fun createPullRequest(base: String, head: String, title: String, body: String, draft: Boolean): Request

    suspend fun comment(number: Long, body: String): String

    suspend fun mergeability(number: Long): Mergeability

    suspend fun merge(number: Long, subject: String, body: String, headSha: String)

    suspend fun getJson(path: String): Any?

    companion object {
        fun of(project: Project): GitHubAccess =
            project.serviceOrNull<GitHubAccess>() ?: throw ToolException(GitHubAvailability.MISSING)
    }
}
