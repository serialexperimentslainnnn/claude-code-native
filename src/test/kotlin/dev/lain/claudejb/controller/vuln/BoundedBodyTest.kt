package dev.lain.claudejb.controller.vuln

import dev.lain.claudejb.model.vuln.ScanSilence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.Flow

class BoundedBodyTest {

    private var cancelled = false

    private fun subscribed(limit: Int) = BoundedBody(limit).apply {
        onSubscribe(
            object : Flow.Subscription {
                override fun request(n: Long) = Unit

                override fun cancel() {
                    cancelled = true
                }
            },
        )
    }

    private fun chunk(text: String) = listOf(ByteBuffer.wrap(text.toByteArray()))

    @Test
    fun `a body within the limit is collected whole`() {
        val body = subscribed(10)
        body.onNext(chunk("hello"))
        body.onNext(chunk(" you"))
        body.onComplete()

        assertEquals("hello you", String(body.body.toCompletableFuture().get()!!))
    }

    @Test
    fun `a body over the limit stops the download and reads as oversized`() {
        val body = subscribed(4)
        body.onNext(chunk("hello"))
        body.onNext(chunk("more"))

        assertTrue(cancelled)
        assertNull(body.body.toCompletableFuture().get())
        assertEquals(OsvAnswer.Silent(ScanSilence.OVERSIZED), OsvHttp.answerOf(200, null))
    }

    @Test
    fun `a refused status is silent whatever the body`() {
        assertEquals(OsvAnswer.Silent(ScanSilence.REFUSED), OsvHttp.answerOf(429, ByteArray(0)))
        assertEquals(OsvAnswer.Silent(ScanSilence.REFUSED), OsvHttp.answerOf(500, null))
        assertEquals(OsvAnswer.Body("{}"), OsvHttp.answerOf(200, "{}".toByteArray()))
    }
}
