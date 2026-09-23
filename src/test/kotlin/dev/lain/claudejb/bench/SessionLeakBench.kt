package dev.lain.claudejb.bench

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.session.ChatSessionManager
import dev.lain.claudejb.controller.session.ClaudeSession
import java.lang.ref.WeakReference

class SessionLeakBench : BasePlatformTestCase() {

    private val manager get() = ChatSessionManager.getInstance(project)

    override fun tearDown() {
        try {
            manager.all().forEach { runCatching { manager.remove(it) } }
        } finally {
            super.tearDown()
        }
    }

    fun `test sessions opened and closed through the manager are released`() {
        val refs = openAndClose()
        BenchReport.record("session_leak", reachableAfterGc(refs).toLong(), "sessions")
    }

    private fun openAndClose(): List<WeakReference<ClaudeSession>> {
        val refs = List(SESSIONS) { WeakReference(manager.create()) }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        refs.forEach { ref -> ref.get()?.let(manager::remove) }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        return refs
    }

    private fun reachableAfterGc(refs: List<WeakReference<ClaudeSession>>): Int {
        repeat(GC_ROUNDS) {
            if (refs.all { it.get() == null }) return 0
            System.gc()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(GC_PAUSE_MS)
        }
        return refs.count { it.get() != null }
    }

    private companion object {
        const val SESSIONS = 10
        const val GC_ROUNDS = 10
        const val GC_PAUSE_MS = 50L
    }
}
