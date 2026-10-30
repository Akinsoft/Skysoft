package com.skysoft.config.core

import io.github.notenoughupdates.moulconfig.observer.Property

fun Float.coerceFiniteIn(min: Float, max: Float, defaultValue: Float): Float =
    (takeIf(Float::isFinite) ?: defaultValue).coerceIn(min, max)

fun Float.coercePositiveIn(min: Float, max: Float, defaultValue: Float): Float =
    (takeIf { it.isFinite() && it > 0f } ?: defaultValue).coerceIn(min, max)

fun Property<Float>.repairFiniteFloat(min: Float, max: Float, defaultValue: Float) {
    set(get().coerceFiniteIn(min, max, defaultValue))
}

fun Property<Float>.repairPositiveFloat(min: Float, max: Float, defaultValue: Float) {
    set(get().coercePositiveIn(min, max, defaultValue))
}

fun MutableList<String>.repairTrimmedEntries(distinctKey: (String) -> String = { it }) {
    val repaired = asSequence().map(String::trim).filter(String::isNotEmpty).distinctBy(distinctKey).toList()
    clear()
    addAll(repaired)
}
