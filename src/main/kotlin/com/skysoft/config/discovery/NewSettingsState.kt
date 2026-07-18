package com.skysoft.config.discovery

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.skysoft.config.SkysoftConfigFileIo
import java.nio.file.Files
import java.nio.file.Path

internal data class NewSettingsDiscoveryState(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val knownSignatures: Map<String, String> = emptyMap(),
    val pendingOptionIds: List<String> = emptyList(),
    val lastPresentedOptionIds: List<String> = emptyList(),
) {
    fun validated(): NewSettingsDiscoveryState {
        require(formatVersion == CURRENT_FORMAT_VERSION) {
            "Unsupported new-settings discovery state version: $formatVersion"
        }
        require(knownSignatures.keys.none(String::isBlank)) { "New-settings discovery state contains a blank option id" }
        require(knownSignatures.values.none(String::isBlank)) {
            "New-settings discovery state contains a blank signature"
        }
        require(pendingOptionIds.none(String::isBlank)) {
            "New-settings discovery state contains a blank pending option id"
        }
        require(lastPresentedOptionIds.none(String::isBlank)) {
            "New-settings discovery state contains a blank presented option id"
        }
        require(pendingOptionIds.distinct().size == pendingOptionIds.size) {
            "New-settings discovery state contains duplicate pending option ids"
        }
        require(lastPresentedOptionIds.distinct().size == lastPresentedOptionIds.size) {
            "New-settings discovery state contains duplicate presented option ids"
        }
        return this
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

internal data class NewSettingsStateUpdate(
    val state: NewSettingsDiscoveryState,
    val addedIds: Set<String>,
    val changedIds: Set<String>,
)

internal fun updateNewSettingsState(
    schema: NewSettingsSchema,
    storedState: NewSettingsDiscoveryState?,
    persistedSignatures: Map<String, String>,
): NewSettingsStateUpdate {
    val previousSignatures = storedState?.knownSignatures ?: persistedSignatures
    val detection = detectNewSettings(previousSignatures, schema.signatures)
    val addedIds = if (storedState == null) {
        detection.addedIds - persistedSignatures.keys
    } else {
        detection.addedIds
    }
    val discoveredIds = addedIds + detection.changedIds
    val state = NewSettingsDiscoveryState(
        knownSignatures = schema.signatures,
        pendingOptionIds = orderedCurrentIds(
            schema,
            storedState.orEmpty().pendingOptionIds + discoveredIds,
        ),
        lastPresentedOptionIds = orderedCurrentIds(schema, storedState.orEmpty().lastPresentedOptionIds),
    )
    return NewSettingsStateUpdate(state, addedIds, detection.changedIds)
}

internal fun orderedCurrentIds(schema: NewSettingsSchema, ids: Iterable<String>): List<String> {
    val requestedIds = ids.toSet()
    return schema.descriptors.map(NewSettingDescriptor::id).filter(requestedIds::contains)
}

private fun NewSettingsDiscoveryState?.orEmpty(): NewSettingsDiscoveryState = this ?: NewSettingsDiscoveryState()

internal class NewSettingsStateStore(private val path: Path) {
    fun load(): NewSettingsDiscoveryState? {
        if (!SkysoftConfigFileIo.hasFileOrBackup(path)) return null
        return SkysoftConfigFileIo.readWithBackup(path) { readablePath ->
            Files.newBufferedReader(readablePath).use { reader ->
                requireNotNull(GSON.fromJson(reader, NewSettingsDiscoveryState::class.java)) {
                    "New-settings discovery state is empty: $readablePath"
                }.validated()
            }
        }
    }

    fun save(state: NewSettingsDiscoveryState) {
        SkysoftConfigFileIo.writeStringSafely(path, GSON.toJson(state.validated()))
    }

    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }
}
