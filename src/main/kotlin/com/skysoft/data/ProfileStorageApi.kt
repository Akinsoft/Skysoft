package com.skysoft.data

import com.google.gson.GsonBuilder
import com.skysoft.SkysoftMod
import com.skysoft.config.MigrationResult
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
        write = { json ->
            SkysoftConfigFiles.writeStringSafely(storagePath, json)
            state.loadedFromDisk = true
        },
        canSave = ::ensureSaveEnabled,
    )
    private val storageChanged = saves::markDirty
    private var saveBlocked = false
    private var saveDisabledWarningShown = false

    val storage: ProfileStorageView.ProfileSpecific
        get() = state.storageData.activeProfile(storageChanged)

    val playerStorage: ProfileStorageView.PlayerSpecific
        get() = state.storageData.activePlayer(storageChanged)

    val allStorage: ProfileStorageView
        get() = state.storageData

    fun updateProfile(action: (ProfileStorage.ProfileSpecific) -> Unit) {
        val profile = state.storageData.activeProfile(storageChanged)
        try {
            action(profile)
        } finally {
            saves.markDirty()
        }
    }

    fun updatePlayer(action: (ProfileStorage.PlayerSpecific) -> Unit) {
        val player = state.storageData.activePlayer(storageChanged)
        try {
            action(player)
        } finally {
            saves.markDirty()
        }
    }

    fun updateAll(action: (ProfileStorage) -> Unit) {
        try {
            action(state.storageData)
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

    fun importLegacyStorage(legacy: ProfileStorage) {
        if (state.loadedFromDisk) return
        updateAll { it.importFrom(legacy) }
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
        val saveDisabledReason = if (SkysoftConfigFiles.migrateProfileStorage() == MigrationResult.READY) {
            null
        } else {
            "legacy ${SkysoftConfigFiles.legacyProfileStorage} could not be copied to $storagePath. " +
                "Move it manually or fix file permissions to save changes."
        }
        if (!SkysoftConfigFiles.hasFileOrBackup(storagePath)) {
            return StorageState(ProfileStorage(), saveDisabledReason, loadedFromDisk = false)
        }
        return loadStorage(saveDisabledReason)
    }

    private fun loadStorage(saveDisabledReason: String?): StorageState = try {
        StorageState(
            storageData = SkysoftConfigFiles.readWithBackup(storagePath, ::readProfileStorage),
            saveDisabledReason = saveDisabledReason,
            loadedFromDisk = true,
        )
    } catch (e: Exception) {
        SkysoftMod.LOGGER.warn("Failed to load Skysoft profile storage or backup from $storagePath", e)
        val storageData = loadFallbackStorage() ?: run {
            SkysoftMod.LOGGER.warn("Using default Skysoft profile storage because no fallback storage could be loaded")
            ProfileStorage()
        }
        StorageState(storageData, storageLoadFailureReason(), loadedFromDisk = true)
    }

    private fun loadFallbackStorage(): ProfileStorage? {
        val fallbackPath = SkysoftConfigFiles.legacyProfileStorage
        if (fallbackPath == storagePath || !Files.isRegularFile(fallbackPath)) return null

        return try {
            readProfileStorage(fallbackPath).also {
                SkysoftMod.LOGGER.warn(
                    "Loaded Skysoft profile storage from legacy path {} because {} failed to load. " +
                        "Saves stay disabled until the current storage file is fixed or deleted.",
                    fallbackPath,
                    storagePath,
                )
            }
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to load fallback Skysoft profile storage from $fallbackPath", e)
            null
        }
    }

    private fun storageLoadFailureReason(): String =
        "$storagePath failed to load. Fix or delete the file to save changes."

    private class StorageState(
        val storageData: ProfileStorage,
        val saveDisabledReason: String?,
        @Volatile var loadedFromDisk: Boolean,
    )
}

private val profileStorageGson = GsonBuilder()
    .excludeFieldsWithoutExposeAnnotation()
    .create()

internal fun readProfileStorage(path: Path): ProfileStorage =
    Files.newBufferedReader(path).use { reader ->
        val storage = profileStorageGson.fromJson(reader, ProfileStorage::class.java)
            ?: error("Skysoft profile storage is empty: $path")
        storage.repairLoadedValues()
        storage
    }
