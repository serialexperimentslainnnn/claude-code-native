package dev.lain.claudejb.controller.vuln

import java.io.ByteArrayOutputStream
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

internal class BoundedBody(private val limit: Int) : HttpResponse.BodySubscriber<ByteArray?> {

    private val collected = ByteArrayOutputStream()
    private val result = CompletableFuture<ByteArray?>()

    @Volatile private var subscription: Flow.Subscription? = null

    override fun getBody(): CompletionStage<ByteArray?> = result

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
        subscription.request(Long.MAX_VALUE)
    }

    override fun onNext(item: List<ByteBuffer>) {
        if (result.isDone) return
        for (buffer in item) {
            if (collected.size() + buffer.remaining() > limit) {
                subscription?.cancel()
                result.complete(null)
                return
            }
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            collected.write(bytes)
        }
    }

    override fun onError(throwable: Throwable) {
        result.completeExceptionally(throwable)
    }

    override fun onComplete() {
        result.complete(collected.toByteArray())
    }
}
