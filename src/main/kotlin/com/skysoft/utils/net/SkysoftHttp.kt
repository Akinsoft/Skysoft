package com.skysoft.utils.net

import com.skysoft.SkysoftMod
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

object SkysoftHttp {
    const val USER_AGENT = "Skysoft"
    val versionedUserAgent = "$USER_AGENT/${SkysoftMod.VERSION}"

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun getString(
        url: String,
        timeout: Duration = Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS),
        maximumResponseBytes: Long = DEFAULT_MAXIMUM_STRING_RESPONSE_BYTES,
    ): CompletableFuture<String> = get(url, timeout, HttpResponse.BodyHandlers.ofString(), maximumResponseBytes)

    fun getBytes(
        url: String,
        timeout: Duration = Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS),
        maximumResponseBytes: Long = DEFAULT_MAXIMUM_BYTE_RESPONSE_BYTES,
    ): CompletableFuture<ByteArray> = get(url, timeout, HttpResponse.BodyHandlers.ofByteArray(), maximumResponseBytes)

    fun sendString(
        request: HttpRequest,
        maximumResponseBytes: Long = DEFAULT_MAXIMUM_STRING_RESPONSE_BYTES,
    ): CompletableFuture<HttpResponse<String>> = send(request, HttpResponse.BodyHandlers.ofString(), maximumResponseBytes)

    private fun <T> get(
        url: String,
        timeout: Duration,
        bodyHandler: HttpResponse.BodyHandler<T>,
        maximumResponseBytes: Long,
    ): CompletableFuture<T> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        return send(request, bodyHandler, maximumResponseBytes).thenApply { response ->
            check(response.statusCode() in 200..299) { "GET $url returned HTTP ${response.statusCode()}" }
            response.body()
        }
    }

    private fun <T> send(
        request: HttpRequest,
        bodyHandler: HttpResponse.BodyHandler<T>,
        maximumResponseBytes: Long,
    ): CompletableFuture<HttpResponse<T>> {
        require(maximumResponseBytes > 0L) { "HTTP response size limit must be positive" }
        return cancellationPropagatingFuture(
            client.sendAsync(request, HttpResponse.BodyHandlers.limiting(bodyHandler, maximumResponseBytes)),
        )
    }

    private const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 30L
    private const val DEFAULT_MAXIMUM_STRING_RESPONSE_BYTES = 16L * 1024L * 1024L
    private const val DEFAULT_MAXIMUM_BYTE_RESPONSE_BYTES = 8L * 1024L * 1024L
}

class PendingHttpRequests {
    private var requests = CancellableRequestGroup()

    fun getString(url: String): CompletableFuture<String> = synchronized(this) {
        requests.track(SkysoftHttp.getString(url))
    }

    fun cancelAll() {
        val cancelled = synchronized(this) {
            requests.also { requests = CancellableRequestGroup() }
        }
        cancelled.cancel()
    }
}

internal class CancellableRequestGroup {
    private val cancellation = RequestCancellation()

    fun <T> track(future: CompletableFuture<T>): CompletableFuture<T> = future.also(cancellation::track)

    fun <T> result(future: CompletableFuture<T>): CompletableFuture<T> {
        cancellation.track(future)
        return CancellationPropagatingFuture<T>(cancellation).also { result ->
            future.whenComplete { value, failure ->
                if (failure == null) {
                    result.complete(value)
                } else {
                    result.completeExceptionally(failure)
                    cancellation.cancel()
                }
            }
        }
    }

    fun cancel() {
        cancellation.cancel()
    }
}

private fun <T> cancellationPropagatingFuture(source: CompletableFuture<T>): CompletableFuture<T> =
    CancellableRequestGroup().result(source)

internal fun Throwable.unwrapFutureFailure(): Throwable {
    var failure = this
    while (failure is CompletionException || failure is ExecutionException) {
        failure = failure.cause ?: return failure
    }
    return failure
}

internal fun Throwable.isCancellationFailure(): Boolean = unwrapFutureFailure() is CancellationException

private class CancellationPropagatingFuture<T>(
    private val cancellation: RequestCancellation,
) : CompletableFuture<T>() {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean =
        super.cancel(mayInterruptIfRunning).also { wasCancelled ->
            if (wasCancelled) cancellation.cancel()
        }

    override fun copy(): CompletableFuture<T> = minimalCompletionStage().toCompletableFuture()

    override fun <U> newIncompleteFuture(): CompletableFuture<U> = CancellationPropagatingFuture(cancellation)
}

private class RequestCancellation {
    private val futures = mutableSetOf<CompletableFuture<*>>()
    private var isCancelled = false

    fun track(future: CompletableFuture<*>) {
        val cancelImmediately = synchronized(this) {
            if (isCancelled) {
                true
            } else {
                futures += future
                false
            }
        }
        if (cancelImmediately) {
            future.cancel(true)
            return
        }
        future.whenComplete { _, _ ->
            synchronized(this) {
                futures -= future
            }
        }
    }

    fun cancel() {
        val active = synchronized(this) {
            if (isCancelled) return
            isCancelled = true
            futures.toList().also { futures.clear() }
        }
        active.forEach { it.cancel(true) }
    }
}
