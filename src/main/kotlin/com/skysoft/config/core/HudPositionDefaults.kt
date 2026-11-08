package com.skysoft.config.core

internal fun rememberHudPositionDefaults(loaded: Any, defaults: Any) {
    loaded.javaClass.persistedFields().forEach { field ->
        val loadedValue = field.get(loaded) ?: return@forEach
        val defaultValue = field.get(defaults) ?: return@forEach
        when {
            loadedValue is HudPosition && defaultValue is HudPosition -> loadedValue.rememberDefault(defaultValue)
            loadedValue.javaClass == defaultValue.javaClass && isSkysoftObject(loadedValue) ->
                rememberHudPositionDefaults(loadedValue, defaultValue)
        }
    }
}
