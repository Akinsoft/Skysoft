package com.skysoft.utils

internal class ActiveConsumerRegistry {
    private val consumers = ActiveListenerRegistry<Unit>()
    private val consumerIds = mutableSetOf<String>()
    private var wasActive = false

    val hasActiveConsumers: Boolean
        get() = consumers.hasActiveListeners

    val isActiveOrDeactivating: Boolean
        get() = hasActiveConsumers || wasActive

    fun register(id: String, isActive: () -> Boolean) {
        check(consumerIds.add(id)) { "Consumer is already registered: $id" }
        consumers.register(id, isActive, Unit)
    }

    fun activity(): ConsumerActivity {
        val isActive = hasActiveConsumers
        val activity = when {
            isActive && !wasActive -> ConsumerActivity.ACTIVATED
            isActive -> ConsumerActivity.ACTIVE
            wasActive -> ConsumerActivity.DEACTIVATED
            else -> ConsumerActivity.INACTIVE
        }
        wasActive = isActive
        return activity
    }

    fun resetActivity() {
        wasActive = false
    }
}

internal enum class ConsumerActivity {
    INACTIVE,
    ACTIVATED,
    ACTIVE,
    DEACTIVATED,
}
