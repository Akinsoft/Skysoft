package com.skysoft.config.core

import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.observer.Property

internal fun configVisibility(isVisible: () -> Boolean): Property<Boolean> = Property.wrap(object : GetSetter<Boolean> {
    override fun get(): Boolean = isVisible()

    override fun set(value: Boolean) = Unit
})
