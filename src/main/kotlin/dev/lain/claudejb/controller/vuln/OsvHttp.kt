package dev.lain.claudejb.controller.vuln

import dev.lain.claudejb.model.vuln.ScanSilence
import dev.lain.claudejb.util.PluginIdentity
import dev.lain.claudejb.util.logger
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal sealed interface OsvAnswer {

    data class Body(val json: String) : OsvAnswer

    data class Silent(val reason: ScanSilence) : OsvAnswer
}

internal object OsvHttp {

    const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

    private const val CONNECT_TIMEOUT_SECONDS = 5L
    private const val REQUEST_TIMEOUT_SECONDS = 20L
    private const val BODY_TIMEOUT_SECONDS = 20L

    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299
    private const val HTTP_TOO_MANY_REQUESTS = 429

    private val LOG = logger<OsvHttp>()

    private val client: HttpClient by lazy {
        val builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
        ProxySelector.getDefault()?.let(builder::proxy)
        builder.build()
    }

    fun post(uri: URI, body: String): OsvAnswer = send(
        HttpRequest.newBuilder(uri)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .header("Content-Type", "application/json"),
        uri,
    )

    private fun send(builder: HttpRequest.Builder, uri: URI): OsvAnswer {
        if (!uri.scheme.equals("https", ignoreCase = true)) return OsvAnswer.Silent(ScanSilence.REFUSED)
        val request = builder
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("User-Agent", PluginIdentity.USER_AGENT)
            .header("Accept", "application/json")
            .build()

        val exchange = client.sendAsync(request, ::subscriberFor)
        return try {
            val response = exchange.get(REQUEST_TIMEOUT_SECONDS + BODY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            answerOf(response.statusCode(), response.body())
        } catch (e: InterruptedException) {
            exchange.cancel(true)
            Thread.currentThread().interrupt()
            OsvAnswer.Silent(ScanSilence.UNREACHABLE)
        } catch (e: TimeoutException) {
            exchange.cancel(true)
            LOG.warn("The vulnerability database did not answer in time; no findings are shown", e)
            OsvAnswer.Silent(ScanSilence.UNREACHABLE)
        } catch (e: ExecutionException) {
            LOG.warn("The vulnerability database could not be reached; no findings are shown", e.cause ?: e)
            OsvAnswer.Silent(ScanSilence.UNREACHABLE)
        }
    }

    private fun subscriberFor(info: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber<ByteArray?> =
        if (info.statusCode() in HTTP_OK_MIN..HTTP_OK_MAX) BoundedBody(MAX_RESPONSE_BYTES) else HttpResponse.BodySubscribers.replacing(null)

    internal fun answerOf(status: Int, body: ByteArray?): OsvAnswer = when {
        status == HTTP_TOO_MANY_REQUESTS -> OsvAnswer.Silent(ScanSilence.REFUSED)
        status !in HTTP_OK_MIN..HTTP_OK_MAX -> OsvAnswer.Silent(ScanSilence.REFUSED)
        body == null -> OsvAnswer.Silent(ScanSilence.OVERSIZED)
        else -> OsvAnswer.Body(String(body, StandardCharsets.UTF_8))
    }
}
