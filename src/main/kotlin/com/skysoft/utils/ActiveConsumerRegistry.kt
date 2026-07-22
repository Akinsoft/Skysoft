package com.skysoft.utils

internal class ActiveConsumerRegistry {
    private val consumers = linkedMapOf<String, () -> Boolean>()
    private var wasActive = false

    val hasActiveConsumers: Boolean
        get() = consumers.values.any { it() }

    val isActiveOrDeactivating: Boolean
        get() = hasActiveConsumers || wasActive

    fun register(id: String, isActive: () -> Boolean) {
        check(consumers.putIfAbsent(id, isActive) == null) { "Consumer is already registered: $id" }
    }

    fun activeConsumerIds(): List<String> = consumers.filterValues { it() }.keys.toList()

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
