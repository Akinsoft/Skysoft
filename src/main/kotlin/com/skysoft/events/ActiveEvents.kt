package com.skysoft.events

import com.skysoft.utils.ActiveListenerRegistry

internal open class ActiveEvents<C> {
    protected val listeners = ActiveListenerRegistry<C>()

    fun register(boundary: String, isActive: () -> Boolean, listener: C) {
        listeners.register(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners
}
