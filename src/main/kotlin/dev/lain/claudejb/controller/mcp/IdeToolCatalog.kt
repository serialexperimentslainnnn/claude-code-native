package dev.lain.claudejb.controller.mcp

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.lain.claudejb.controller.git.GitAvailability
import dev.lain.claudejb.controller.mcp.tools.code.AnalysisTools
import dev.lain.claudejb.controller.mcp.tools.code.AnalyzeTools
import dev.lain.claudejb.controller.mcp.tools.code.BookmarkTools
import dev.lain.claudejb.controller.mcp.tools.code.DiagnosticsTools
import dev.lain.claudejb.controller.mcp.tools.code.EditOpsTools
import dev.lain.claudejb.controller.mcp.tools.code.EditTools
import dev.lain.claudejb.controller.mcp.tools.code.EditorTools
import dev.lain.claudejb.controller.mcp.tools.code.FileTools
import dev.lain.claudejb.controller.mcp.tools.code.FormatTools
import dev.lain.claudejb.controller.mcp.tools.code.HierarchyTools
import dev.lain.claudejb.controller.mcp.tools.code.IndexTools
import dev.lain.claudejb.controller.mcp.tools.code.InspectTools
import dev.lain.claudejb.controller.mcp.tools.code.LanguageTools
import dev.lain.claudejb.controller.mcp.tools.code.MarkupTools
import dev.lain.claudejb.controller.mcp.tools.code.NavigateTools
import dev.lain.claudejb.controller.mcp.tools.code.OutlineTools
import dev.lain.claudejb.controller.mcp.tools.code.PresenceTools
import dev.lain.claudejb.controller.mcp.tools.code.PsiTools
import dev.lain.claudejb.controller.mcp.tools.code.ReadTools
import dev.lain.claudejb.controller.mcp.tools.code.RecentTools
import dev.lain.claudejb.controller.mcp.tools.code.RefactorOpsTools
import dev.lain.claudejb.controller.mcp.tools.code.RefactorTools
import dev.lain.claudejb.controller.mcp.tools.code.SearchTools
import dev.lain.claudejb.controller.mcp.tools.code.TemplateTools
import dev.lain.claudejb.controller.mcp.tools.code.UastDomain
import dev.lain.claudejb.controller.mcp.tools.code.ViewTools
import dev.lain.claudejb.controller.mcp.tools.code.WorkspaceTools
import dev.lain.claudejb.controller.mcp.tools.ops.ActionTools
import dev.lain.claudejb.controller.mcp.tools.ops.ConsoleTools
import dev.lain.claudejb.controller.mcp.tools.ops.DbTools
import dev.lain.claudejb.controller.mcp.tools.ops.HttpTools
import dev.lain.claudejb.controller.mcp.tools.ops.IdeTools
import dev.lain.claudejb.controller.mcp.tools.ops.NotifyTools
import dev.lain.claudejb.controller.mcp.tools.ops.ProjectTools
import dev.lain.claudejb.controller.mcp.tools.ops.RemoteTools
import dev.lain.claudejb.controller.mcp.tools.ops.ServiceTools
import dev.lain.claudejb.controller.mcp.tools.ops.ServiceViewTools
import dev.lain.claudejb.controller.mcp.tools.ops.SshTools
import dev.lain.claudejb.controller.mcp.tools.ops.ToolsMenuTools
import dev.lain.claudejb.controller.mcp.tools.ops.WindowTools
import dev.lain.claudejb.controller.mcp.tools.run.BreakpointTools
import dev.lain.claudejb.controller.mcp.tools.run.BuildTools
import dev.lain.claudejb.controller.mcp.tools.run.DebugTools
import dev.lain.claudejb.controller.mcp.tools.run.RunOpsTools
import dev.lain.claudejb.controller.mcp.tools.run.RunTools
import dev.lain.claudejb.controller.mcp.tools.run.TerminalTools
import dev.lain.claudejb.controller.mcp.tools.run.TestTools
import dev.lain.claudejb.controller.mcp.tools.vcs.ChangesTools
import dev.lain.claudejb.controller.mcp.tools.vcs.ForgeTools
import dev.lain.claudejb.controller.mcp.tools.vcs.GitReadTools
import dev.lain.claudejb.controller.mcp.tools.vcs.GitWriteTools
import dev.lain.claudejb.controller.mcp.tools.vcs.HistoryTools
import dev.lain.claudejb.controller.mcp.tools.vcs.LogOpsTools
import dev.lain.claudejb.controller.mcp.tools.vcs.PullRequestOpsTools
import dev.lain.claudejb.controller.mcp.tools.vcs.ReleaseTools
import dev.lain.claudejb.model.mcp.ToolCatalog
import dev.lain.claudejb.model.mcp.ToolDomain
import dev.lain.claudejb.model.session.launch.IdeServer
import kotlinx.coroutines.CoroutineScope

internal object IdeToolCatalog {

    private class Kit(val p: Project, val s: CoroutineScope) {
        val reveal = Reveal(p)
        val actions = IdeActions(p, s)
    }

    private val KIT: Key<Kit> = Key.create("claude.mcp.tool.kit")

    private val DOMAINS: Map<IdeServer, List<(Kit) -> ToolDomain?>> = mapOf(
        IdeServer.CODE to listOf(
            { k -> ReadTools(k.p, k.reveal).domain() },
            { k -> SearchTools(k.p).domain() },
            { k -> NavigateTools(k.p).domain() },
            { k -> OutlineTools(k.p).domain() },
            { k -> DiagnosticsTools(k.p, k.reveal).domain() },
            { k -> InspectTools(k.p).domain() },
            { k -> EditTools(k.p, k.reveal).domain() },
            { k -> EditOpsTools(k.p, k.reveal, k.actions).domain() },
            { k -> RefactorTools(k.p).domain() },
            { k -> FormatTools(k.p).domain() },
            { k -> EditorTools(k.p, k.reveal, k.actions).domain() },
            { k -> HierarchyTools(k.p).domain() },
            { k -> RecentTools(k.p, k.actions).domain() },
            { k -> AnalyzeTools(k.p, k.actions).domain() },
            { k -> AnalysisTools(k.p, k.actions).domain() },
            { k -> ViewTools(k.p, k.actions).domain() },
            { k -> FileTools(k.p, k.actions, k.reveal).domain() },
            { k -> RefactorOpsTools(k.actions).domain() },
            { k -> TemplateTools(k.p, TargetContext(k.p), k.reveal).domain() },
            { k -> LanguageTools(k.p, k.actions).domain() },
            { k -> BookmarkTools(k.p, k.reveal).domain() },
            { k -> PsiTools(k.p, k.reveal).domain() },
            { k -> IndexTools(k.p).domain() },
            { k -> k.p.serviceOrNull<UastDomain>()?.domain() },
            { k -> WorkspaceTools(k.p).domain() },
            { k -> MarkupTools(k.p, TargetContext(k.p), k.reveal).domain() },
            { k -> PresenceTools(k.p, k.reveal).domain() },
        ),
        IdeServer.RUN to listOf(
            { k -> BuildTools(k.p, k.s).domain() },
            { k -> RunTools(k.p, k.s).domain() },
            { k -> TestTools(k.p, k.s).domain() },
            { k -> TerminalTools(k.p, k.s).domain() },
            { k -> DebugTools(k.p).domain() },
            { k -> BreakpointTools(k.p).domain() },
            { k -> RunOpsTools(k.p, k.actions, k.reveal).domain() },
        ),
        IdeServer.VCS to listOf(
            { k -> GitReadTools(k.p, k.reveal).domain() },
            { k -> GitWriteTools(k.p).domain() },
            { k -> ForgeTools(k.p, k.actions, k.reveal).domain() },
            { k -> LogOpsTools(k.p, k.actions).domain() },
            { k -> ChangesTools(k.p, k.actions, k.reveal).domain() },
            { k -> HistoryTools(k.p, k.actions, k.reveal).domain() },
            { k -> PullRequestOpsTools(k.p, k.reveal).domain() },
            { k -> ReleaseTools(k.p).domain() },
        ),
        IdeServer.OPS to listOf(
            { k -> ServiceTools(k.p, k.s).domain() },
            { k -> ProjectTools(k.p).domain() },
            { k -> IdeTools(k.p, k.actions, k.s).domain() },
            { k -> ActionTools(k.p, k.actions).domain() },
            { k -> WindowTools(k.p, k.actions).domain() },
            { k -> ServiceViewTools(k.p, k.s).domain() },
            { k -> RemoteTools(k.p, k.actions, k.reveal).domain() },
            { k -> ToolsMenuTools(k.p, k.actions).domain() },
            { k -> ConsoleTools(k.p, k.actions).domain() },
            { k -> NotifyTools(k.p).domain() },
            { k -> DbTools(k.p).domain() },
            { k -> HttpTools(k.p, k.s, k.reveal).takeIf { it.available() }?.domain() },
            { k -> SshTools(k.p).takeIf { it.available() }?.domain() },
        ),
    )

    private val REQUIRES: Map<IdeServer, () -> Boolean> = mapOf(IdeServer.VCS to GitAvailability::isGitPluginEnabled)

    fun catalog(server: IdeServer, project: Project, scope: CoroutineScope): ToolCatalog {
        if (REQUIRES[server]?.invoke() == false) return ToolCatalog(emptyList())
        val kit = project.getUserData(KIT)?.takeIf { it.s === scope } ?: Kit(project, scope).also { project.putUserData(KIT, it) }
        return ToolCatalog(DOMAINS[server].orEmpty().mapNotNull { it(kit) })
    }
}
