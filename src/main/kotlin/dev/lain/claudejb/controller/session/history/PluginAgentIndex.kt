package dev.lain.claudejb.controller.session.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import dev.lain.claudejb.model.session.agents.AgentMeta
import dev.lain.claudejb.model.session.agents.AgentNode
import dev.lain.claudejb.model.session.history.SessionStore
import dev.lain.claudejb.model.settings.SecretStore
import dev.lain.claudejb.model.settings.SettingsScope
import dev.lain.claudejb.util.logger
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class PluginAgentIndex internal constructor(
    private val scope: SettingsScope,
    private val basePath: String?,
    private val later: (Runnable) -> Unit = ::afterQuietPeriod,
    private val sessionExists: (String) -> Boolean = SessionStore::exists,
) : Disposable {

    constructor(project: Project) : this(SettingsScope.of(project), project.basePath)

    private val log = logger<PluginAgentIndex>()

    object Kind {
        const val AGENT = "agent"
        const val SUBAGENT = "subagent"
        const val TASK = "backgroundtask"
        const val CHAT = "chat"
    }

    @Serializable
    data class Ref(val type: String, val id: String)

    @Serializable
    data class Node(
        val type: String,
        val id: String,
        val parent: Ref? = null,
        val childs: List<Ref> = emptyList(),
        val agentType: String? = null,
        val toolUseId: String? = null,
        val open: Boolean = true,
        val closedByUser: Boolean = false,
    )

    @Serializable
    data class SessionRecord(val nodes: List<Node> = emptyList())

    @Serializable
    data class Index(
        val version: Int = FORMAT_VERSION,
        val sessions: Map<String, SessionRecord> = emptyMap(),
    )

    private val cache = LinkedHashMap<String, SessionRecord>()
    private var loaded = false
    private var dirty = false
    private var flushQueued = false

    @Synchronized
    fun admit(sessionId: String, node: AgentNode) {
        val id = AgentMeta.bareAgentId(node.agentId)
        val parentId = node.parentAgentId?.let { AgentMeta.bareAgentId(it) }
        upsert(
            sessionId,
            id,
        ) { existing ->
            Node(
                type = if (parentId == null) Kind.AGENT else Kind.SUBAGENT,
                id = id,
                parent = parentId?.let { Ref(parentTypeOf(sessionId, it), it) } ?: Ref(Kind.CHAT, sessionId),
                agentType = node.meta.agentType,
                open = existing?.open ?: true,
                closedByUser = existing?.closedByUser ?: false,
            )
        }
    }

    @Synchronized
    fun admittedAgents(sessionId: String): List<String> = agents(sessionId).map { it.id }

    @Synchronized
    fun openAgents(sessionId: String): List<String> =
        agents(sessionId).filter { it.open && !it.closedByUser }.map { it.id }

    @Synchronized
    fun nodes(sessionId: String): List<Node> = session(sessionId).nodes

    @Synchronized
    fun setTabOpen(sessionId: String, agentId: String, open: Boolean) {
        val id = AgentMeta.bareAgentId(agentId)
        upsert(sessionId, id) { existing ->
            (existing ?: Node(type = Kind.AGENT, id = id, parent = Ref(Kind.CHAT, sessionId)))
                .copy(open = open, closedByUser = !open)
        }
    }

    @Synchronized
    fun recordTask(sessionId: String, taskId: String, toolUseId: String?, ownerAgentId: String?) {
        val owner = ownerAgentId?.let { AgentMeta.bareAgentId(it) }
        upsert(sessionId, taskId) { existing ->
            Node(
                type = Kind.TASK,
                id = taskId,
                parent = owner?.let { Ref(parentTypeOf(sessionId, it), it) }
                    ?: existing?.parent
                    ?: Ref(Kind.CHAT, sessionId),
                toolUseId = toolUseId ?: existing?.toolUseId,
                open = existing?.open ?: true,
                closedByUser = existing?.closedByUser ?: false,
            )
        }
    }

    @TestOnly
    @Synchronized
    fun taskIds(sessionId: String): List<String> =
        session(sessionId).nodes.filter { it.type == Kind.TASK }.map { it.id }

    @Synchronized
    fun forget(sessionId: String) {
        if (load().remove(sessionId) != null) flush()
    }

    private fun agents(sessionId: String): List<Node> =
        session(sessionId).nodes.filter { it.type == Kind.AGENT || it.type == Kind.SUBAGENT }

    private fun parentTypeOf(sessionId: String, parentId: String): String =
        session(sessionId).nodes.firstOrNull { it.id == parentId }?.type ?: Kind.AGENT

    private fun upsert(sessionId: String, id: String, build: (Node?) -> Node) {
        val session = session(sessionId)
        val nodes = session.nodes.toMutableList()
        val i = nodes.indexOfFirst { it.id == id }
        val next = build(nodes.getOrNull(i))
        if (i >= 0 && nodes[i] == next) return
        if (i >= 0) nodes[i] = next else nodes += next
        cache[sessionId] = SessionRecord(nodes)
        flush()
    }

    private fun session(sessionId: String): SessionRecord =
        load().getOrPut(sessionId) { SessionRecord() }

    private fun load(): LinkedHashMap<String, SessionRecord> {
        if (!loaded) {
            cache.clear()
            SharedPluginFiles.migrate(basePath)
            val body = SecretStore.get(scope.agentIndexName).orEmpty()
            cache.putAll(decode(body))
            loaded = true
            if (body.isNotBlank() && !body.contains("\"version\":$FORMAT_VERSION")) flush()
            val stored = cache.keys.toList()
            if (stored.isNotEmpty()) later(Runnable { prune(stored) })
        }
        return cache
    }

    private fun prune(stored: List<String>) {
        val gone = stored.filterNot { runCatching { sessionExists(it) }.getOrDefault(true) }
        if (gone.isEmpty()) return
        synchronized(this) {
            gone.forEach(cache::remove)
            flush()
        }
    }

    private fun flush() {
        dirty = true
        if (flushQueued) return
        flushQueued = true
        later(Runnable { flushNow() })
    }

    @Synchronized
    private fun flushNow() {
        flushQueued = false
        if (!dirty) return
        dirty = false
        runCatching { SecretStore.set(scope.agentIndexName, encode(cache)) }
            .onFailure { log.warn("could not persist the agent index", it) }
    }

    override fun dispose() = flushNow()

    companion object {
        const val FORMAT_VERSION = 3

        private const val QUIET_PERIOD_MS = 2_000L

        private fun afterQuietPeriod(task: Runnable) {
            AppExecutorUtil.getAppScheduledExecutorService().schedule(task, QUIET_PERIOD_MS, TimeUnit.MILLISECONDS)
        }

        fun getInstance(project: Project): PluginAgentIndex = project.service()

        fun encode(sessions: Map<String, SessionRecord>): String = AgentIndexCodec.encode(sessions)

        fun decode(text: String): LinkedHashMap<String, SessionRecord> = AgentIndexCodec.decode(text)
    }
}
