package com.skysoft.config.core

import io.github.notenoughupdates.moulconfig.observer.Property

fun Property<Float>.repairFiniteFloat(min: Float, max: Float, defaultValue: Float) {
    val repaired = get().takeIf { it.isFinite() } ?: defaultValue
    set(repaired.coerceIn(min, max))
}

fun Property<Float>.repairPositiveFloat(min: Float, max: Float, defaultValue: Float) {
    val current = get()
    val repaired = if (!current.isFinite() || current <= 0f) {
        defaultValue
    } else {
        current
    }
    set(repaired.coerceIn(min, max))
}
