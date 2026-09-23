package dev.lain.claudejb.controller.session

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import dev.lain.claudejb.controller.process.ClaudeBinaryLocator
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.env.RemoteMounts
import dev.lain.claudejb.model.settings.env.requiresTrustPrompt
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SessionGates(
    private val s: ClaudeSession,
    private val project: Project,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
    private val onBinary: (missing: Boolean) -> Unit,
) {

    private enum class Trust { UNASKED, ASKING, DECLINED }

    @Volatile private var trust = Trust.UNASKED

    @Volatile private var refusedRoot: String? = null

    private val ticking = AtomicBoolean(false)

    val backoff = BootBackoff()

    fun tickStarted(): Boolean = ticking.compareAndSet(false, true)

    fun tickDone() = ticking.set(false)

    fun forgive() {
        if (trust == Trust.DECLINED) trust = Trust.UNASKED
        refusedRoot = null
        backoff.forgive()
    }

    fun mayAutoStart(): Boolean = trust == Trust.UNASKED && refusedRoot == null && backoff.mayStart()

    fun admit(settings: ClaudeSettings): File? {
        val binary = resolveBinary(settings) ?: return null
        if (settings.requiresTrustPrompt()) {
            if (trust == Trust.UNASKED) askTrust(settings)
            return null
        }
        val root = project.basePath
        if (root != null && root == refusedRoot) return null
        if (RemoteMounts.isRemote(root)) {
            refusedRoot = root
            refuseRemoteProject(root)
            return null
        }
        return binary
    }

    private fun resolveBinary(settings: ClaudeSettings): File? {
        val binary = ClaudeBinaryLocator.locate(settings.claudePath)
        onBinary(binary == null)
        if (binary == null) {
            s.notifier.missingBinary()
            return null
        }
        if (settings.claudePath != binary.absolutePath) {
            settings.update { it.claudePath = binary.absolutePath }
        }
        return binary
    }

    private fun askTrust(settings: ClaudeSettings) {
        trust = Trust.ASKING
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed || s.lifecycle.disposed) {
                    trust = Trust.UNASKED
                    return@invokeLater
                }
                val trusted = s.notifier.ensureExecTrust(settings)
                trust = if (trusted) Trust.UNASKED else Trust.DECLINED
                if (trusted) s.start()
            },
            ModalityState.nonModal(),
        )
    }

    private fun refuseRemoteProject(root: String?) {
        val msg = SessionNotifier.remoteProjectRefusal(root)
        edt {
            s.transcript.add(Speaker.ERROR, msg)
            fireState()
        }
        s.notifier.error(msg)
    }
}
