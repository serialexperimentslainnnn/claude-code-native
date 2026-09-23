package dev.lain.claudejb.controller.mcp.tools.vcs

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.github.GitHubAvailability
import dev.lain.claudejb.controller.mcp.tools.vcs.GitHubAccess.Branches
import dev.lain.claudejb.controller.mcp.tools.vcs.GitHubAccess.Check
import dev.lain.claudejb.controller.mcp.tools.vcs.GitHubAccess.Head
import dev.lain.claudejb.controller.mcp.tools.vcs.GitHubAccess.Mergeability
import dev.lain.claudejb.controller.mcp.tools.vcs.GitHubAccess.Repository
import dev.lain.claudejb.controller.mcp.tools.vcs.GitHubAccess.Request
import dev.lain.claudejb.model.mcp.ToolException
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.io.IOException

internal class GitHubGateway(private val project: Project) : GitHubAccess {

    private fun requireGitHub() {
        GitHubAvailability.require()
    }

    override fun repository(): Repository {
        requireGitHub()
        val coordinates = mapping().repository
        val path = coordinates.repositoryPath
        return Repository(coordinates.serverPath.toString(), path.owner, path.repository)
    }

    override suspend fun pullRequests(state: String, max: Int): List<Request> {
        requireGitHub()
        val (executor, coordinates) = client()
        val query = "repo:${slug(coordinates)} type:pr $state sort:updated-desc"
        val page = GraphQLRequestPagination(afterCursor = null, pageSize = max)
        val request = GHGQLRequests.PullRequest.search(coordinates.serverPath, query, page)
        val response = api { executor.executeSuspend(request) }
        return response.nodes.map { Request(head(it), it.updatedAt.toInstant().toString()) }
    }

    override suspend fun pullRequest(number: Long): Request {
        requireGitHub()
        val (executor, coordinates) = client()
        val pr = api { executor.executeSuspend(GHGQLRequests.PullRequest.findOne(coordinates, number)) }
            ?: throw ToolException("no pull request #$number in " + slug(coordinates))
        val branches = Branches(pr.baseRefName, pr.headRefName, pr.body, pr.reviewDecision?.name?.lowercase().orEmpty(), pr.headRefOid)
        return Request(head(pr), pr.updatedAt.toInstant().toString(), branches)
    }

    override suspend fun createPullRequest(base: String, head: String, title: String, body: String, draft: Boolean): Request {
        requireGitHub()
        val (executor, coordinates) = client()
        val repository = api { executor.executeSuspend(GHGQLRequests.Repo.find(coordinates)) }
            ?: throw ToolException("GitHub does not know " + slug(coordinates) + " for this account")
        val request = GHGQLRequests.PullRequest.create(coordinates, repository.id, base, head, title, body, draft)
        val created = api { executor.executeSuspend(request) }
        return Request(head(created), created.updatedAt.toInstant().toString())
    }

    override suspend fun comment(number: Long, body: String): String {
        requireGitHub()
        val (executor, coordinates) = client()
        return api { executor.executeSuspend(GithubApiRequests.Repos.Issues.Comments.create(coordinates, number, body)) }.htmlUrl
    }

    override suspend fun mergeability(number: Long): Mergeability {
        requireGitHub()
        val (executor, coordinates) = client()
        val data = api { executor.executeSuspend(GHGQLRequests.PullRequest.mergeabilityData(coordinates, number)) }
            ?: throw ToolException("no pull request #$number in " + slug(coordinates))
        val commit = data.commits.nodes.lastOrNull()?.commit
        val statuses = commit?.status?.contexts.orEmpty().map {
            Check(it.context, it.state.name.lowercase(), it.isRequired, it.targetUrl.orEmpty())
        }
        val runs = commit?.checkSuites?.nodes.orEmpty().flatMap { it.checkRuns?.nodes.orEmpty() }.map {
            Check(it.name, it.conclusion?.name?.lowercase() ?: PENDING, it.isRequired, it.url)
        }
        val state = data.mergeStateStatus
        val head = commit?.oid.orEmpty()
        return Mergeability(data.mergeable.name.lowercase(), state.name.lowercase(), state.canMerge(), head, statuses + runs)
    }

    override suspend fun merge(number: Long, subject: String, body: String, headSha: String) {
        requireGitHub()
        val (executor, coordinates) = client()
        val path = coordinates.repositoryPath
        val request = GithubApiRequests.Repos.PullRequests.merge(coordinates.serverPath, path, number, subject, body, headSha)
        api { executor.executeSuspend(request) }
    }

    override suspend fun getJson(path: String): Any? {
        requireGitHub()
        val (executor, coordinates) = client()
        val url = GithubApiRequests.getUrl(coordinates.serverPath, "/repos/" + slug(coordinates) + path)
        return api { executor.executeSuspend(GithubApiRequest.Get.Json(url, Any::class.java)) }
    }

    private suspend fun client(): Pair<GithubApiRequestExecutor, GHRepositoryCoordinates> {
        val coordinates = mapping().repository
        val account = GHAccountsUtil.getSingleOrDefaultAccount(project)
            ?: throw ToolException("no GitHub account is signed in; add one in Settings ▸ Version Control ▸ GitHub")
        val token = service<GHAccountManager>().findCredentials(account)
            ?: throw ToolException("the IDE holds no token for the GitHub account ${account.name}; sign in again")
        return GithubApiRequestExecutor.Factory.getInstance().create(account.server, token) to coordinates
    }

    private fun mapping() = project.service<GHHostedRepositoriesManager>().knownRepositoriesState.value.firstOrNull()
        ?: throw ToolException("no remote of this project points at a GitHub repository the IDE knows")

    private fun slug(coordinates: GHRepositoryCoordinates) = coordinates.repositoryPath.owner + "/" + coordinates.repositoryPath.repository

    private fun head(pr: GHPullRequestShort) =
        Head(pr.number, pr.title, pr.state.name.lowercase(), pr.isDraft, pr.author?.login.orEmpty(), pr.url)

    private companion object {
        const val PENDING = "pending"
    }

    private suspend fun <T> api(block: suspend () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw ToolException("GitHub did not answer: ${e.message}", e)
    }
}
