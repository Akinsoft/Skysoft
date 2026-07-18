package com.skysoft.config.discovery

import com.google.gson.JsonObject

internal sealed interface NewSettingsConfigSource {
    data object Fresh : NewSettingsConfigSource

    data class Loaded(val json: JsonObject) : NewSettingsConfigSource

    data class Unavailable(val reason: String) : NewSettingsConfigSource
}

internal object NewSettingsConfigBootstrap {
    private var source: NewSettingsConfigSource = NewSettingsConfigSource.Unavailable("Config has not loaded")

    @Synchronized
    fun captureFreshConfig() {
        source = NewSettingsConfigSource.Fresh
    }

    @Synchronized
    fun captureLoadedConfig(json: JsonObject) {
        source = NewSettingsConfigSource.Loaded(json.deepCopy())
    }

    @Synchronized
    fun captureUnavailableConfig(reason: String) {
        source = NewSettingsConfigSource.Unavailable(reason)
    }

    @Synchronized
    fun take(): NewSettingsConfigSource = source.also {
        source = NewSettingsConfigSource.Unavailable("Config bootstrap snapshot was already consumed")
    }
}
