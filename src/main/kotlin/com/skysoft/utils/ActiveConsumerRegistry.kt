package com.skysoft.utils

internal class ActiveConsumerRegistry {
    private val consumers = linkedMapOf<String, () -> Boolean>()

    val hasActiveConsumers: Boolean
        get() = consumers.values.any { it() }

    fun register(id: String, isActive: () -> Boolean) {
        check(consumers.putIfAbsent(id, isActive) == null) { "Consumer is already registered: $id" }
    }

    fun activeConsumerIds(): List<String> = consumers.filterValues { it() }.keys.toList()
}
