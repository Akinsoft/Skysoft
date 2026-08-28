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
    private var saveBlocked = false
    private var saveDisabledWarningShown = false

    val storage: ProfileStorage.ProfileSpecific
        get() = state.storageData.activeProfile()

    val playerStorage: ProfileStorage.PlayerSpecific
        get() = state.storageData.activePlayer()

    val allStorage: ProfileStorage
        get() = state.storageData

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

    internal val hasActiveConsumers: Boolean
        get() = consumers.hasActiveConsumers

    fun importLegacyStorage(legacy: ProfileStorage) {
        if (state.loadedFromDisk) return
        state.storageData.importFrom(legacy)
        markDirty()
    }

    fun markDirty() {
        saves.markDirty()
    }

    internal fun flush() {
        saves.flush()
    }

    private fun serializeStorage(): String {
        state.storageData.repairLoadedValues()
        return profileStorageGson.toJson(state.storageData)
    }

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
        val storageState = StorageState(
            saveDisabledReason = saveDisabledReason,
            loadedFromDisk = SkysoftConfigFiles.hasFileOrBackup(storagePath),
        )
        storageState.storageData = loadStorage(storageState)
        return storageState
    }

    private fun loadStorage(storageState: StorageState): ProfileStorage {
        if (!storageState.loadedFromDisk) return ProfileStorage()
        return try {
            SkysoftConfigFiles.readWithBackup(storagePath) { path ->
                readStorage(path)
            }
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to load Skysoft profile storage or backup from $storagePath", e)
            storageState.saveDisabledReason = storageLoadFailureReason()
            loadFallbackStorage() ?: run {
                SkysoftMod.LOGGER.warn("Using default Skysoft profile storage because no fallback storage could be loaded")
                ProfileStorage()
            }
        }
    }

    private fun loadFallbackStorage(): ProfileStorage? {
        val fallbackPath = SkysoftConfigFiles.legacyProfileStorage
        if (fallbackPath == storagePath || !Files.isRegularFile(fallbackPath)) return null

        return try {
            readStorage(fallbackPath).also {
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

    private fun readStorage(path: Path): ProfileStorage =
        readProfileStorage(path)

    private class StorageState(
        var saveDisabledReason: String?,
        @Volatile var loadedFromDisk: Boolean,
    ) {
        lateinit var storageData: ProfileStorage
    }
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
