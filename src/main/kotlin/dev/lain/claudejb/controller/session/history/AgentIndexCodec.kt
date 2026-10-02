package dev.lain.claudejb.controller.session.history

import dev.lain.claudejb.controller.session.history.PluginAgentIndex.Index
import dev.lain.claudejb.controller.session.history.PluginAgentIndex.Kind
import dev.lain.claudejb.controller.session.history.PluginAgentIndex.Node
import dev.lain.claudejb.controller.session.history.PluginAgentIndex.Ref
import dev.lain.claudejb.controller.session.history.PluginAgentIndex.SessionRecord
import dev.lain.claudejb.model.session.agents.AgentMeta
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object AgentIndexCodec {

    private val JSON = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(sessions: Map<String, SessionRecord>): String {
        val withChildren = sessions.mapValues { (_, rec) ->
            SessionRecord(
                rec.nodes.map { node ->
                    node.copy(
                        childs = rec.nodes
                            .filter { it.parent?.id == node.id }
                            .map { Ref(it.type, it.id) },
                    )
                },
            )
        }
        return runCatching { JSON.encodeToString(Index(PluginAgentIndex.FORMAT_VERSION, withChildren)) }.getOrDefault("")
    }

    fun decode(text: String): LinkedHashMap<String, SessionRecord> {
        val out = LinkedHashMap<String, SessionRecord>()
        if (text.isBlank()) return out
        runCatching { JSON.decodeFromString<Index>(text) }.getOrNull()?.let { index ->
            if (index.sessions.isNotEmpty()) {
                index.sessions.forEach { (id, rec) -> out[id] = rec.normalised(id) }
                return out
            }
        }
        runCatching { JSON.decodeFromString<Map<String, List<LegacyRecord>>>(text) }.getOrNull()
            ?.forEach { (id, legacy) ->
                out[id] = SessionRecord(
                    legacy.map {
                        Node(
                            type = Kind.AGENT,
                            id = AgentMeta.bareAgentId(it.agentId),
                            parent = Ref(Kind.CHAT, id),
                            open = it.open,
                            closedByUser = it.closedByUser,
                        )
                    },
                ).normalised(id)
            }
        return out
    }

    @Serializable
    private data class LegacyRecord(
        val agentId: String,
        val open: Boolean = true,
        val closedByUser: Boolean = false,
    )

    private fun SessionRecord.normalised(sessionId: String): SessionRecord {
        val seen = LinkedHashMap<String, Node>()
        nodes.forEach { n ->
            val id = if (n.type == Kind.TASK) n.id else AgentMeta.bareAgentId(n.id)
            val parent = n.parent?.let {
                if (it.type == Kind.CHAT) Ref(Kind.CHAT, sessionId) else Ref(it.type, AgentMeta.bareAgentId(it.id))
            }
            seen.putIfAbsent(id, n.copy(id = id, parent = parent ?: Ref(Kind.CHAT, sessionId)))
        }
        return SessionRecord(seen.values.toList())
    }
}
