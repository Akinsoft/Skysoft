package com.skysoft.utils.net

import com.skysoft.SkysoftMod
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import net.minecraft.client.Minecraft

internal class AsyncRequestSlot<T>(
    private val completionExecutor: Executor? = null,
    private val disposeStaleResult: (T) -> Unit = {},
) {
    private var request: CompletableFuture<T>? = null

    val isPending: Boolean
        get() = synchronized(this) { request != null }

    fun pendingFuture(): CompletableFuture<T>? = synchronized(this) { request?.copy() }

    fun startIfIdle(
        requestFactory: () -> CompletableFuture<T>,
        completion: (T?, Throwable?) -> Unit,
    ) {
        startIfIdleFuture(requestFactory, completion)
    }

    fun startIfIdleFuture(
        requestFactory: () -> CompletableFuture<T>,
        completion: (T?, Throwable?) -> Unit,
    ): CompletableFuture<T>? = synchronized(this) {
        if (request != null) return null
        requestFactory().also { startLocked(it, completion) }
    }

    fun replace(
        nextRequest: CompletableFuture<T>,
        completion: (T?, Throwable?) -> Unit,
    ) {
        val previous = synchronized(this) {
            request.also { startLocked(nextRequest, completion) }
        }
        previous?.cancel(true)
    }

    fun invalidate() {
        synchronized(this) { request = null }
    }

    fun cancel() {
        val current = synchronized(this) { request.also { request = null } }
        current?.cancel(true)
    }

    private fun startLocked(
        nextRequest: CompletableFuture<T>,
        completion: (T?, Throwable?) -> Unit,
    ) {
        request = nextRequest
        nextRequest.whenCompleteOn(completionExecutor) { value, failure ->
            complete(nextRequest, value, failure, completion)
        }
    }

    private fun complete(
        completedRequest: CompletableFuture<T>,
        value: T?,
        failure: Throwable?,
        completion: (T?, Throwable?) -> Unit,
    ) {
        val accepted = synchronized(this) {
            if (request !== completedRequest) {
                false
            } else {
                request = null
                true
            }
        }
        if (accepted) {
            completion(value, failure)
        } else if (failure == null && value != null) {
            disposeStaleResult(value)
        }
    }
}

internal class KeyedAsyncRequestSlots<K, T>(
    private val completionExecutor: Executor? = null,
) {
    private val requests = mutableMapOf<K, CompletableFuture<T>>()

    fun startIfIdle(
        key: K,
        requestFactory: () -> CompletableFuture<T>,
        completion: (T?, Throwable?) -> Unit,
    ) {
        synchronized(this) {
            if (key in requests) return
            startLocked(key, requestFactory(), completion)
        }
    }

    fun getOrStart(
        key: K,
        requestFactory: () -> CompletableFuture<T>,
        completion: (T?, Throwable?) -> Unit,
    ): CompletableFuture<T> = synchronized(this) {
        requests[key]?.copy() ?: requestFactory().also { startLocked(key, it, completion) }.copy()
    }

    fun cancel(key: K) {
        val pending = synchronized(this) { requests.remove(key) }
        pending?.cancel(true)
    }

    fun cancelAll() {
        val pending = synchronized(this) {
            requests.values.toList().also { requests.clear() }
        }
        pending.forEach { it.cancel(true) }
    }

    private fun startLocked(
        key: K,
        request: CompletableFuture<T>,
        completion: (T?, Throwable?) -> Unit,
    ) {
        requests[key] = request
        request.whenCompleteOn(completionExecutor) { value, failure ->
            val accepted = synchronized(this) {
                if (requests[key] !== request) false else {
                    requests.remove(key)
                    true
                }
            }
            if (accepted) completion(value, failure)
        }
    }
}

private fun <T> CompletableFuture<T>.whenCompleteOn(executor: Executor?, action: (T?, Throwable?) -> Unit) {
    if (executor == null) {
        whenComplete { value, failure -> action(value, failure) }
    } else {
        whenCompleteAsync({ value, failure -> action(value, failure) }, executor)
    }
}

internal class RefreshSchedule {
    @Volatile
    private var nextAtMillis: Long = 0L

    fun isDue(nowMillis: Long): Boolean = nowMillis >= nextAtMillis

    fun schedule(nowMillis: Long, delayMillis: Long) {
        require(delayMillis >= 0L) { "Refresh delay must not be negative" }
        nextAtMillis = nowMillis + delayMillis
    }

    fun reset() {
        nextAtMillis = 0L
    }
}

internal class PeriodicRefresh<T>(
    private val name: String,
    private val description: String,
    private val url: String,
    private val refreshIntervalMillis: Long,
    private val failureRetryMillis: Long,
    private val parse: (String) -> T,
    private val onSuccess: (T) -> Unit,
) {
    private val requests = PendingHttpRequests()
    private val requestSlot = AsyncRequestSlot<T>(completionExecutor = Minecraft.getInstance())
    private val schedule = RefreshSchedule()

    fun register() {
        SkysoftClientEvents.onClientStopping("$name request cancellation") { cancel() }
    }

    fun refreshIfDue() {
        if (schedule.isDue(System.currentTimeMillis())) refresh()
    }

    fun refresh() {
        requestSlot.startIfIdle(
            requestFactory = { requests.getString(url).thenApply(parse) },
        ) { response, error ->
            SkysoftErrorBoundary.run("$name async completion") {
                val now = System.currentTimeMillis()
                if (error == null && response != null) {
                    onSuccess(response)
                    schedule.schedule(now, refreshIntervalMillis)
                } else if (error?.isCancellationFailure() != true) {
                    schedule.schedule(now, failureRetryMillis)
                    SkysoftMod.LOGGER.warn("Failed to refresh $description", error)
                }
            }
        }
    }

    fun resetSchedule() {
        schedule.reset()
    }

    fun reset() {
        cancel()
        schedule.reset()
    }

    private fun cancel() {
        requestSlot.cancel()
        requests.cancelAll()
    }
}
