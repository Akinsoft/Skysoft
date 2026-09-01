package com.skysoft.utils

internal class ActiveStatePublisher<T>(
    private val boundary: String,
    initialState: T,
) {
    private val listeners = ActiveListenerRegistry<(T) -> Unit>()
    private var registered = false

    var state: T = initialState
        private set

    var version: Long = 0L
        private set

    val hasActiveListeners: Boolean
        get() = listeners.hasActiveListeners

    fun register() {
        if (registered) return
        registered = true
        SkysoftClientEvents.onEndTick(
            "$boundary listener activation",
            isActive = { true },
        ) {
            listeners.forEachNewlyActive { listener -> listener(state) }
        }
    }

    fun onChange(
        listenerBoundary: String,
        isActive: () -> Boolean,
        listener: (T) -> Unit,
    ) {
        listeners.register(listenerBoundary, isActive, listener)
    }

    fun update(nextState: T) {
        if (state == nextState) return
        state = nextState
        version++
        listeners.forEachActive { listener -> listener(nextState) }
    }
}
