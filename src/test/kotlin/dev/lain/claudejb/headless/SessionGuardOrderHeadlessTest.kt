package dev.lain.claudejb.headless

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.session.ClaudeSession
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SessionGuardOrderHeadlessTest : BasePlatformTestCase() {

    fun `test guard steps leave the caller's thread and keep their order`() {
        val session = ClaudeSession(project, "t")
        try {
            val seen = Collections.synchronizedList(mutableListOf<Int>())
            val threads = ConcurrentHashMap.newKeySet<Thread>()
            val done = CountDownLatch(1)
            (1..50).forEach { i ->
                session.guard.inOrder {
                    threads += Thread.currentThread()
                    seen += i
                }
            }
            session.guard.inOrder { done.countDown() }

            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals((1..50).toList(), seen.toList())
            assertFalse(Thread.currentThread() in threads)
        } finally {
            session.dispose()
        }
    }

    fun `test a failing step does not stop the ones after it`() {
        val session = ClaudeSession(project, "t")
        try {
            val done = CountDownLatch(1)
            session.guard.inOrder { error("boom") }
            session.guard.inOrder { done.countDown() }

            assertTrue(done.await(10, TimeUnit.SECONDS))
        } finally {
            session.dispose()
        }
    }
}
