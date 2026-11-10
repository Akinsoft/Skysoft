package com.skysoft.data

import com.google.gson.GsonBuilder
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.SkysoftClientEvents
import java.nio.file.Files
import java.nio.file.Path

object ProfileStorageApi {
    private val consumers = ActiveConsumerRegistry()
    private val storagePath: Path = SkysoftConfigFiles.profileStorage
    private val state: StorageState by lazy(::initializeStorage)
    private val saves = BackgroundSave(
        name = "Skysoft profile storage",
        prepare = ::serializeStorage,
        write = { json -> SkysoftConfigFiles.writeStringSafely(storagePath, json) },
        canSave = ::ensureSaveEnabled,
    )
    private val storageChanged = saves::markDirty
    private var saveBlocked = false
    private var saveDisabledWarningShown = false

    val storage: ProfileStorageView.ProfileSpecific
        get() = state.storageData.activeProfile(storageChanged)

    val playerStorage: ProfileStorageView.PlayerSpecific
        get() = state.storageData.activePlayer(storageChanged)

    fun updateProfile(action: (ProfileStorage.ProfileSpecific) -> Unit) =
        update(state.storageData.activeProfile(storageChanged), action)

    fun updatePlayer(action: (ProfileStorage.PlayerSpecific) -> Unit) =
        update(state.storageData.activePlayer(storageChanged), action)

    fun updateAll(action: (ProfileStorage) -> Unit) = update(state.storageData, action)

    private fun <T> update(target: T, action: (T) -> Unit) {
        try {
            action(target)
        } finally {
            saves.markDirty()
        }
    }

    fun register() {
        SkyBlockProfileApi.registerConsumer("Profile Storage") { consumers.hasActiveConsumers }
        SkysoftClientEvents.onEndTick(
            "Profile Storage autosave",
            isActive = { consumers.hasActiveConsumers || hasSchedulableChanges() },
        ) {
            if (hasSchedulableChanges()) saves.saveIfDue()
        }
        SkysoftClientEvents.onDisconnect("Profile Storage disconnect save") { saves.saveInBackground() }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    internal fun flush() {
        saves.flush()
    }

    private fun serializeStorage(): String = profileStorageGson.toJson(state.storageData)

    private fun hasSchedulableChanges(): Boolean = saves.hasUnsavedChanges && !saveBlocked

    private fun ensureSaveEnabled(): Boolean {
        val reason = state.saveDisabledReason ?: return true
        saveBlocked = true
        if (!saveDisabledWarningShown) {
            saveDisabledWarningShown = true
            SkysoftMod.LOGGER.warn("Skipping Skysoft profile storage save because $reason")
        }
        return false
    }

    private fun initializeStorage(): StorageState {
        if (!SkysoftConfigFiles.hasFileOrBackup(storagePath)) return StorageState(ProfileStorage(), saveDisabledReason = null)
        return try {
            StorageState(SkysoftConfigFiles.readWithBackup(storagePath, ::readProfileStorage), saveDisabledReason = null)
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to load Skysoft profile storage or backup from $storagePath, using defaults", e)
            StorageState(ProfileStorage(), "$storagePath failed to load. Fix or delete the file to save changes.")
        }
    }

    private class StorageState(
        val storageData: ProfileStorage,
        val saveDisabledReason: String?,
    )
}

private val profileStorageGson = GsonBuilder()
    .excludeFieldsWithoutExposeAnnotation()
    .create()

private fun readProfileStorage(path: Path): ProfileStorage =
    Files.newBufferedReader(path).use { reader ->
        val storage = profileStorageGson.fromJson(reader, ProfileStorage::class.java)
            ?: error("Skysoft profile storage is empty: $path")
        storage.repairLoadedValues()
        storage
    }
