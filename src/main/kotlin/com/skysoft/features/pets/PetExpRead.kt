package com.skysoft.features.pets

internal data class PetExpRead(
    val value: Double,
    val exact: Boolean,
)

internal val PetExpRead?.exactValue get() = this?.takeIf { it.exact }?.value
