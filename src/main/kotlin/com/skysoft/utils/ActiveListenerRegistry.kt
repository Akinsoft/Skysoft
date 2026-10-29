package com.skysoft.utils

internal class ActiveListenerRegistry<C> {
    private var listeners: List<ActiveListener<C>> = emptyList()

    val hasActiveListeners: Boolean
        get() = listeners.any(::isActive)

    fun register(boundary: String, isActive: () -> Boolean, callback: C) {
        listeners += ActiveListener(boundary, isActive, callback)
    }

    fun forEachActive(action: (C) -> Unit) {
        dispatch(onlyNewlyActive = false, action)
    }

    fun forEachNewlyActive(action: (C) -> Unit) {
        dispatch(onlyNewlyActive = true, action)
    }

    fun <R> foldActive(initial: R, action: (R, C) -> R): R =
        listeners.fold(initial) { result, listener ->
            if (isActive(listener)) {
                SkysoftErrorBoundary.value(listener.boundary, result) { action(result, listener.callback) }
            } else {
                result
            }
        }

    fun anyActive(predicate: (C) -> Boolean): Boolean =
        listeners.any { listener ->
            isActive(listener) && SkysoftErrorBoundary.value(listener.boundary, false) { predicate(listener.callback) }
        }

    private fun dispatch(onlyNewlyActive: Boolean, action: (C) -> Unit) {
        listeners.forEach { listener ->
            val active = isActive(listener)
            val shouldRun = active && !(onlyNewlyActive && listener.wasActive)
            listener.wasActive = active
            if (shouldRun) SkysoftErrorBoundary.run(listener.boundary) { action(listener.callback) }
        }
    }

    private fun isActive(listener: ActiveListener<C>): Boolean =
        SkysoftErrorBoundary.value(listener.activityBoundary, false, listener.isActive)

    private data class ActiveListener<C>(
        val boundary: String,
        val isActive: () -> Boolean,
        val callback: C,
        var wasActive: Boolean = false,
    ) {
        val activityBoundary = "$boundary activity"
    }
}
