package dev.lain.claudejb.controller.mcp

import dev.lain.claudejb.mcp.Frames
import dev.lain.claudejb.mcp.StdioBridge
import dev.lain.claudejb.model.mcp.JsonRpc
import dev.lain.claudejb.model.mcp.McpServer
import dev.lain.claudejb.model.mcp.TokenRing
import dev.lain.claudejb.model.mcp.rethrowIfCancelled
import dev.lain.claudejb.model.mcp.toon.Toon
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.util.thisLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class ServerEndpoint(
    val server: IdeServer,
    val socket: Path,
    private val mcp: McpServer,
    private val tokens: TokenRing,
    private val scope: CoroutineScope,
    private val admit: suspend () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = thisLogger()
    private val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
        .also { ownerOnly(socket) }
    private val inFlight = AtomicInteger()
    private val clients: MutableSet<SocketChannel> = ConcurrentHashMap.newKeySet()

    fun start(): Job = scope.launch(io) {
        supervisorScope {
            while (isActive) {
                val client = withContext(io) { runCatching { channel.accept() }.getOrNull() } ?: break
                clients += client
                launch { attend(client) }
            }
        }
    }

    fun close() {
        runCatching { channel.close() }
        clients.forEach { runCatching { it.close() } }
    }

    private suspend fun attend(client: SocketChannel) {
        try {
            runCatching { if (admit()) Connection(client).serve() }.onFailure { cause ->
                if (cause !is Exception) throw cause
                rethrowIfCancelled(cause)
                log.warn("${server.key}: a connection failed", cause)
            }
        } finally {
            release(client)
        }
    }

    private fun release(client: SocketChannel) {
        clients -= client
        runCatching { client.close() }
    }

    private inner class Connection(client: SocketChannel) {

        private val input: InputStream = BufferedInputStream(Channels.newInputStream(client))
        private val output: OutputStream = Channels.newOutputStream(client)
        private val writing = Mutex()
        private val jobs = ConcurrentHashMap<String, Job>()

        suspend fun serve() = withContext(io) {
            try {
                while (true) {
                    val frame = runCatching { Frames.read(input) }.getOrNull() ?: break
                    receive(frame)
                }
            } finally {
                jobs.values.forEach { it.cancel() }
            }
        }

        private suspend fun receive(frame: String) {
            val message = runCatching { Toon.decode(frame) }.getOrElse {
                log.debug { "${server.key}: unreadable frame" }
                send(JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Parse error"))
                return
            }
            runCatching {
                when (val parsed = JsonRpc.parse(message)) {
                    is JsonRpc.Request -> request(parsed, message)
                    is JsonRpc.Notification -> notification(parsed, message)
                    else -> mcp.handle(message)?.let { send(it) }
                }
            }.onFailure { cause ->
                if (cause !is Exception) throw cause
                rethrowIfCancelled(cause)
                log.warn("${server.key}: a frame could not be handled", cause)
                idOf(message)?.let { send(JsonRpc.error(it, JsonRpc.INTERNAL_ERROR, FAILED)) }
            }
        }

        private suspend fun request(request: JsonRpc.Request, message: JsonElement) {
            if (!authorized(request.params)) {
                log.warn("${server.key}: rejected a request to ${request.method} without a valid token")
                send(JsonRpc.error(request.id, JsonRpc.INVALID_REQUEST, REJECTED))
                return
            }
            if (inFlight.get() >= QUEUE_DEPTH) {
                val busy = "$QUEUE_DEPTH requests are already in flight on ${server.key}; retry when one answers"
                send(JsonRpc.error(request.id, JsonRpc.INTERNAL_ERROR, busy))
                return
            }
            val key = request.id.toString()
            inFlight.incrementAndGet()
            val job = scope.launch(start = CoroutineStart.LAZY) { answer(request.id, message)?.let { send(it) } }
            jobs[key] = job
            job.invokeOnCompletion {
                inFlight.decrementAndGet()
                jobs.remove(key, job)
            }
            job.start()
        }

        private suspend fun answer(id: JsonElement, message: JsonElement): JsonObject? =
            runCatching { mcp.handle(message) }.getOrElse { cause ->
                if (cause !is Exception) throw cause
                rethrowIfCancelled(cause)
                log.warn("${server.key}: a request failed", cause)
                JsonRpc.error(id, JsonRpc.INTERNAL_ERROR, FAILED)
            }

        private suspend fun notification(notification: JsonRpc.Notification, message: JsonElement) {
            if (!authorized(notification.params)) return
            if (notification.method == CANCELLED) {
                notification.params["requestId"]?.let { jobs[it.toString()]?.cancel() }
                return
            }
            mcp.handle(message)
        }

        private fun authorized(params: JsonObject): Boolean =
            tokens.accepts((JsonRpc.meta(params)[StdioBridge.TOKEN_KEY] as? JsonPrimitive)?.content)

        private fun idOf(message: JsonElement): JsonElement? = (message as? JsonObject)?.get("id")?.takeUnless { it is JsonNull }

        private suspend fun send(reply: JsonObject) {
            val text = encoded(reply)
            writing.withLock { withContext(io) { runCatching { Frames.write(output, text) } } }
        }

        private fun encoded(reply: JsonObject): String =
            runCatching { Toon.encode(reply) }.getOrElse { cause ->
                if (cause !is RuntimeException) throw cause
                log.warn("${server.key}: a reply could not be encoded", cause)
                Toon.encode(JsonRpc.error(reply["id"], JsonRpc.INTERNAL_ERROR, UNENCODABLE))
            }
    }

    companion object {
        const val QUEUE_DEPTH = 16
        const val REJECTED = "request rejected"
        const val FAILED = "the IDE failed while answering; the server stays up, retry or narrow the request"
        const val UNENCODABLE = "the IDE's answer could not be encoded as TOON; narrow the request"
        private const val CANCELLED = "notifications/cancelled"
        private val OWNER_ONLY = PosixFilePermissions.fromString("rw-------")

        private fun ownerOnly(socket: Path) {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) Files.setPosixFilePermissions(socket, OWNER_ONLY)
        }
    }
}
