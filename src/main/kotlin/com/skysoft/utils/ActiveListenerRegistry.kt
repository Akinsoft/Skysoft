package com.skysoft.utils

internal class ActiveListenerRegistry<C> {
    private var listeners: List<ActiveListener<C>> = emptyList()

    val hasActiveListeners: Boolean
        get() = listeners.any(::isActive)

    fun register(boundary: String, isActive: () -> Boolean, callback: C) {
        listeners += ActiveListener(boundary, isActive, callback)
    }

    fun forEachActive(action: (C) -> Unit) {
        listeners.forEach { listener ->
            val active = isActive(listener)
            listener.wasActive = active
            if (active) {
                SkysoftErrorBoundary.run(listener.boundary) { action(listener.callback) }
            }
        }
    }

    fun forEachNewlyActive(action: (C) -> Unit) {
        listeners.forEach { listener ->
            val active = isActive(listener)
            val newlyActive = active && !listener.wasActive
            listener.wasActive = active
            if (newlyActive) {
                SkysoftErrorBoundary.run(listener.boundary) { action(listener.callback) }
            }
        }
    }

    fun <R> foldActive(initial: R, action: (R, C) -> R): R =
        listeners.fold(initial) { result, listener ->
            if (isActive(listener)) {
                SkysoftErrorBoundary.value(listener.boundary, result) { action(result, listener.callback) }
            } else {
                result
            }
        }

    fun anyActive(predicate: (C) -> Boolean): Boolean {
        listeners.forEach { listener ->
            if (!isActive(listener)) return@forEach
            val matches = SkysoftErrorBoundary.value(listener.boundary, false) { predicate(listener.callback) }
            if (matches) return true
        }
        return false
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
