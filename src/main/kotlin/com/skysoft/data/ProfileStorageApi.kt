package com.skysoft.data

import com.google.gson.GsonBuilder
import com.skysoft.SkysoftMod
import com.skysoft.config.MigrationResult
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

object ProfileStorageApi {
    private val storagePath: Path = SkysoftConfigFiles.profileStorage
    private var saveDisabledReason: String? = if (SkysoftConfigFiles.migrateProfileStorage() == MigrationResult.READY) {
        null
    } else {
        "legacy ${SkysoftConfigFiles.legacyProfileStorage} could not be copied to $storagePath. " +
            "Move it manually or fix file permissions to save changes."
    }
    private var loadedFromDisk = SkysoftConfigFiles.hasFileOrBackup(storagePath)
    private var jsonNeedsSave = false
    private val storageData: ProfileStorage = loadStorage()
    private var lastSaved = ElapsedTimeMark.farPast()

    val storage: ProfileStorage.ProfileSpecific
        get() = storageData.activeProfile()

    val playerStorage: ProfileStorage.PlayerSpecific
        get() = storageData.activePlayer()

    val allStorage: ProfileStorage
        get() = storageData

    fun register() {
        SkysoftClientEvents.onEndTick("Profile Storage autosave") {
            if (jsonNeedsSave && lastSaved.passedSince() >= 30.seconds) {
                saveNow()
            }
        }
        SkysoftClientEvents.onDisconnect("Profile Storage disconnect save", ::saveNow)
    }

    fun importLegacyStorage(legacy: ProfileStorage) {
        if (loadedFromDisk) return
        storageData.importFrom(legacy)
        markDirty()
    }

    fun markDirty() {
        jsonNeedsSave = true
    }

    fun saveNow() {
        if (!jsonNeedsSave) return

        lastSaved = ElapsedTimeMark.now()
        if (saveDisabledReason != null) {
            SkysoftMod.LOGGER.warn("Skipping Skysoft profile storage save because $saveDisabledReason")
            return
        }

        try {
            storageData.repairLoadedValues()
            val json = profileStorageGson.toJson(storageData)
            SkysoftConfigFiles.writeStringSafely(storagePath, json)
            loadedFromDisk = true
            jsonNeedsSave = false
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to save Skysoft profile storage", e)
        }
    }

    private fun loadStorage(): ProfileStorage {
        if (!loadedFromDisk) return ProfileStorage()
        return try {
            SkysoftConfigFiles.readWithBackup(storagePath) { path ->
                readStorage(path)
            }
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to load Skysoft profile storage or backup from $storagePath", e)
            saveDisabledReason = storageLoadFailureReason()
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
