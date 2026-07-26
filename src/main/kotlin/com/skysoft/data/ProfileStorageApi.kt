package com.skysoft.data

import com.google.gson.GsonBuilder
import com.skysoft.SkysoftMod
import com.skysoft.config.MigrationResult
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import net.minecraft.util.Util

object ProfileStorageApi {
    private val consumers = ActiveConsumerRegistry()
    private val storagePath: Path = SkysoftConfigFiles.profileStorage
    private val state: StorageState by lazy(::initializeStorage)
    private val saveLock = Any()
    private var changeVersion = 0L
    private var savedVersion = 0L
    private var pendingSave: CompletableFuture<Void>? = null
    private var saveBlocked = false
    private var saveDisabledWarningShown = false
    private var lastSaveAttempt = ElapsedTimeMark.farPast()

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
            if (hasSchedulableChanges() && lastSaveAttempt.passedSince() >= 30.seconds) saveInBackground()
        }
        SkysoftClientEvents.onDisconnect("Profile Storage disconnect save", ::saveNow)
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
        synchronized(saveLock) {
            changeVersion++
        }
    }

    fun saveNow() {
        if (pendingSave() == null && !hasUnsavedChanges()) return
        lastSaveAttempt = ElapsedTimeMark.now()
        waitForPendingSave()
        if (!hasUnsavedChanges() || !ensureSaveEnabled()) return

        try {
            val save = prepareSave()
            SkysoftConfigFiles.writeStringSafely(storagePath, save.json)
            recordSaved(save.version)
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to save Skysoft profile storage", e)
        }
    }

    private fun saveInBackground() {
        if (pendingSave() != null || !hasUnsavedChanges()) return
        lastSaveAttempt = ElapsedTimeMark.now()
        if (!ensureSaveEnabled()) return

        val save = try {
            prepareSave()
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to prepare Skysoft profile storage save", e)
            return
        }
        val request = CompletableFuture.runAsync(
            { SkysoftConfigFiles.writeStringSafely(storagePath, save.json) },
            Util.ioPool(),
        )
        synchronized(saveLock) {
            pendingSave = request
        }
        request.whenComplete { _, failure ->
            synchronized(saveLock) {
                if (pendingSave === request) pendingSave = null
                if (failure == null) savedVersion = max(savedVersion, save.version)
            }
            if (failure == null) {
                state.loadedFromDisk = true
            } else {
                SkysoftMod.LOGGER.error("Failed to save Skysoft profile storage", failure)
            }
        }
    }

    private fun prepareSave(): PreparedStorageSave {
        state.storageData.repairLoadedValues()
        val version = synchronized(saveLock) { changeVersion }
        return PreparedStorageSave(version, profileStorageGson.toJson(state.storageData))
    }

    private fun recordSaved(version: Long) {
        synchronized(saveLock) {
            savedVersion = max(savedVersion, version)
        }
        state.loadedFromDisk = true
    }

    private fun waitForPendingSave() {
        val request = pendingSave() ?: return
        runCatching(request::join)
    }

    private fun pendingSave(): CompletableFuture<Void>? = synchronized(saveLock) { pendingSave }

    private fun hasUnsavedChanges(): Boolean = synchronized(saveLock) { changeVersion > savedVersion }

    private fun hasSchedulableChanges(): Boolean = synchronized(saveLock) {
        changeVersion > savedVersion && !saveBlocked
    }

    private fun ensureSaveEnabled(): Boolean {
        val reason = state.saveDisabledReason ?: return true
        val shouldLog = synchronized(saveLock) {
            saveBlocked = true
            if (saveDisabledWarningShown) {
                false
            } else {
                saveDisabledWarningShown = true
                true
            }
        }
        if (shouldLog) SkysoftMod.LOGGER.warn("Skipping Skysoft profile storage save because $reason")
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

    private data class PreparedStorageSave(val version: Long, val json: String)
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
