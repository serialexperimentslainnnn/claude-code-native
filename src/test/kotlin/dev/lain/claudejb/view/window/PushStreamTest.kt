package dev.lain.claudejb.view.window

import dev.lain.claudejb.rpc.FrontendChannel
import dev.lain.claudejb.rpc.PagePush
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushStreamTest {

    private class Recorder : PushSink {
        val pushes = mutableListOf<PagePush>()
        var closed = false

        override fun push(push: PagePush) {
            pushes += push
        }

        override fun close() {
            closed = true
        }

        fun methods() = pushes.map { it.method }
    }

    private var resyncs = 0

    private fun stream(cap: Int = PushStream.DEFAULT_CAP) = PushStream(cap) { resyncs++ }

    @Test
    fun `a late collector gets the latest snapshot of each method, then the transcript from a clear`() {
        val stream = stream()
        stream.emit(PagePush("meta", "1"))
        stream.emit(PagePush("state", "a"))
        stream.emit(PagePush("batch", "[1]"))
        stream.emit(PagePush("meta", "2"))
        stream.emit(PagePush("append", "{\"id\":1,\"delta\":\"x\"}"))

        val sink = Recorder()
        stream.attach(sink)

        assertEquals(listOf("meta", "state", "clear", "batch", "append"), sink.methods())
        assertEquals("2", sink.pushes.first().json)
    }

    @Test
    fun `a clear drops the transcript that came before it`() {
        val stream = stream()
        stream.emit(PagePush("batch", "[1]"))
        stream.emit(PagePush("clear", PushStream.NO_ARGS))
        stream.emit(PagePush("batch", "[2]"))

        val sink = Recorder()
        stream.attach(sink)

        assertEquals(listOf(PagePush("clear", PushStream.NO_ARGS), PagePush("batch", "[2]")), sink.pushes)
    }

    @Test
    fun `a one-shot command waits for the first collector and is delivered once`() {
        val stream = stream()
        stream.emit(PagePush(FrontendChannel.FOCUS, PushStream.NO_ARGS))

        val first = Recorder()
        stream.attach(first)
        val second = Recorder()
        stream.attach(second)

        assertTrue(FrontendChannel.FOCUS in first.methods())
        assertTrue(FrontendChannel.FOCUS !in second.methods())
    }

    @Test
    fun `a one-shot command sent while a page listens is never replayed`() {
        val stream = stream()
        val sink = Recorder()
        stream.attach(sink)
        stream.emit(PagePush(FrontendChannel.COPY, "\"text\""))
        sink.pushes.clear()

        stream.replay()

        assertTrue(FrontendChannel.COPY !in sink.methods())
    }

    @Test
    fun `ready replays to every collector already attached`() {
        val stream = stream()
        val sink = Recorder()
        stream.attach(sink)
        stream.emit(PagePush("session", "{}"))
        sink.pushes.clear()

        stream.replay()

        assertEquals(listOf("session", "clear"), sink.methods())
    }

    @Test
    fun `an overflowing transcript falls back to a resync instead of replaying a partial one`() {
        val stream = stream(cap = 2)
        repeat(3) { stream.emit(PagePush("append", "{}")) }

        val sink = Recorder()
        stream.attach(sink)

        assertEquals(1, resyncs)
        assertTrue("append" !in sink.methods() && "clear" !in sink.methods())

        stream.emit(PagePush("clear", PushStream.NO_ARGS))
        stream.emit(PagePush("batch", "[]"))
        val later = Recorder()
        stream.attach(later)
        assertEquals(1, resyncs)
        assertEquals(listOf("clear", "batch"), later.methods())
    }

    @Test
    fun `a detached collector hears nothing more, and closing closes the ones left`() {
        val stream = stream()
        val gone = Recorder()
        val stays = Recorder()
        val detach = stream.attach(gone)
        stream.attach(stays)
        detach()

        stream.emit(PagePush("meta", "{}"))
        stream.close()

        assertTrue("meta" !in gone.methods())
        assertTrue("meta" in stays.methods())
        assertTrue(stays.closed)
    }

    @Test
    fun `the vibe switch is a snapshot the frontend gets back on every replay`() {
        assertEquals(PushStream.Kind.SNAPSHOT, PushStream.kindOf(FrontendChannel.VIBE))
        assertEquals(PushStream.Kind.TRANSIENT, PushStream.kindOf(FrontendChannel.BROWSE))
        assertEquals(PushStream.Kind.TRANSCRIPT, PushStream.kindOf("trimRows"))
    }
}
