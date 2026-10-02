package dev.lain.claudejb.controller.session

import dev.lain.claudejb.controller.mcp.AdmissionGrant
import dev.lain.claudejb.controller.mcp.IdeMcpService
import dev.lain.claudejb.controller.process.ClaudeProcess
import dev.lain.claudejb.model.protocol.ClaudeEvent
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.model.session.launch.SessionLauncher
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.util.thisLogger
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class SessionProcess(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
    private val fireAttention: (AttentionReason) -> Unit,
    private val onEvent: (ClaudeEvent) -> Unit,
) {

    private val log = thisLogger()

    private val slot = GenerationSlot<ClaudeProcess>()

    val generation: Int get() = slot.generation

    @Volatile private var resumedLaunch = false

    fun isRunning(): Boolean = slot.current?.isRunning() == true

    fun write(line: String): Boolean = slot.current?.writeLine(line) ?: false

    fun supersede(): Int = slot.supersede()

    fun terminate() {
        slot.take()?.terminate()
    }

    fun spawn(launchGen: Int, binary: File, workDir: File, env: Map<String, String>, resume: Boolean): Boolean {
        if (launchGen != generation) return false
        val sockets = ideSockets()
        if (launchGen != generation) return false
        val opts = s.launch.copy(sessionId = s.sessionId, ideSockets = sockets)
        val grant = AtomicReference<AdmissionGrant?>()
        val proc = ClaudeProcess(
            binary = binary,
            workDir = workDir,
            args = SessionLauncher.buildArgs(opts, resume, SessionLauncher.mcpConfigJson(opts)),
            nodeOverride = ClaudeSettings.getInstance(s.project).nodePath,
            extraEnv = env,
            onEvent = onEvent,
            onTerminated = { code ->
                grant.getAndSet(null)?.withdraw()
                onTerminated(launchGen, code)
            },
        )
        val started = runCatching { proc.start() }
        if (started.isFailure) {
            log.warn("Failed to start the claude process", started.exceptionOrNull())
            s.notifier.error("Failed to start Claude Code: ${started.exceptionOrNull()?.message ?: "unknown error"}")
            return false
        }
        if (!slot.publish(launchGen, proc) { resumedLaunch = resume }) {
            proc.terminate()
            return false
        }
        grant.set(admit(sockets.size))
        if (!proc.isRunning()) grant.getAndSet(null)?.withdraw()
        return true
    }

    private fun ideSockets(): Map<IdeServer, String> {
        if (!s.launch.ideIntegration) return emptyMap()
        return runCatching { IdeMcpService.getInstance(s.project).sockets() }
            .onFailure { log.warn("The IDE MCP servers could not start; the session runs without them", it) }
            .getOrDefault(emptyMap())
    }

    private fun admit(count: Int): AdmissionGrant? =
        if (count > 0) IdeMcpService.getInstance(s.project).expectConnections(count) else null

    private fun onTerminated(gen: Int, exitCode: Int) {
        if (gen != generation) return
        val staleResume = resumedLaunch && !s.catalog.initialized
        s.flushDeltas()
        s.controlClient.failAll("process gone")
        edt {
            s.turn.reset()
            s.lifecycle.ready = false
            s.catalog.initialized = false
            s.prompts.dropSuggestion()
            s.cardManager.clear()
            s.taskTracker.clear()
            s.hookNarrator.clear()
            when {
                exitCode != 0 && staleResume -> restartFresh(exitCode)

                exitCode != 0 -> {
                    s.transcript.add(Speaker.ERROR, "Claude Code exited (code $exitCode).")
                    s.notifier.error("Claude Code exited unexpectedly (code $exitCode).")
                    fireAttention(AttentionReason.ERROR)
                    fireState()
                }

                else -> {
                    s.systemNotice("Session ended.")
                    fireState()
                }
            }
        }
    }

    private fun restartFresh(exitCode: Int) {
        log.info("resume of session ${s.sessionId} failed (exit $exitCode) — continuing as a new conversation")
        s.sessionId = null
        resumedLaunch = false
        s.systemNotice("That conversation is no longer available — started a new one.")
        fireState()
        s.start(resume = false)
    }
}

internal class GenerationSlot<T : Any> {

    @Volatile var generation = 0
        private set

    @Volatile var current: T? = null
        private set

    @Synchronized
    fun supersede(): Int = ++generation

    @Synchronized
    fun publish(gen: Int, value: T, onPublish: () -> Unit = {}): Boolean {
        if (gen != generation) return false
        onPublish()
        current = value
        return true
    }

    @Synchronized
    fun take(): T? = current.also { current = null }
}
