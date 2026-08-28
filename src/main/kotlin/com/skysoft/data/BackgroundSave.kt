package com.skysoft.data

import com.skysoft.SkysoftMod
import com.skysoft.utils.ElapsedTimeMark
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds
import net.minecraft.util.Util

internal class BackgroundSave<T>(
    private val name: String,
    private val prepare: () -> T,
    private val write: (T) -> Unit,
    private val canSave: () -> Boolean = { true },
) {
    private val lock = Any()
    private var changeVersion = 0L
    private var savedVersion = 0L
    private var pendingSave: CompletableFuture<Void>? = null
    private var lastSaveAttempt = ElapsedTimeMark.farPast()

    val hasUnsavedChanges: Boolean
        get() = synchronized(lock) { changeVersion > savedVersion }

    fun markDirty() {
        synchronized(lock) {
            changeVersion++
        }
    }

    fun saveIfDue() {
        if (lastSaveAttempt.passedSince() < SAVE_INTERVAL || !hasUnsavedChanges) return
        saveInBackground()
    }

    fun saveInBackground() {
        if (pendingSave() != null || !hasUnsavedChanges) return
        lastSaveAttempt = ElapsedTimeMark.now()
        if (!canSave()) return

        val save = try {
            prepareSave()
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to prepare $name save", e)
            return
        }
        val request = try {
            CompletableFuture.runAsync({ write(save.value) }, Util.ioPool())
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to start $name save", e)
            return
        }
        synchronized(lock) {
            pendingSave = request
        }
        request.whenComplete { _, failure ->
            synchronized(lock) {
                if (pendingSave === request) pendingSave = null
                if (failure == null) savedVersion = maxOf(savedVersion, save.version)
            }
            if (failure != null) SkysoftMod.LOGGER.error("Failed to save $name", failure)
        }
    }

    fun flush() {
        if (pendingSave() == null && !hasUnsavedChanges) return
        lastSaveAttempt = ElapsedTimeMark.now()
        waitForPendingSave()
        if (!hasUnsavedChanges || !canSave()) return

        try {
            val save = prepareSave()
            write(save.value)
            synchronized(lock) {
                savedVersion = maxOf(savedVersion, save.version)
            }
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to save $name", e)
        }
    }

    private fun prepareSave(): PreparedSave<T> {
        val version = synchronized(lock) { changeVersion }
        return PreparedSave(version, prepare())
    }

    private fun waitForPendingSave() {
        val request = pendingSave() ?: return
        runCatching(request::join)
    }

    private fun pendingSave(): CompletableFuture<Void>? = synchronized(lock) { pendingSave }

    private data class PreparedSave<T>(val version: Long, val value: T)

    private companion object {
        val SAVE_INTERVAL = 30.seconds
    }
}
