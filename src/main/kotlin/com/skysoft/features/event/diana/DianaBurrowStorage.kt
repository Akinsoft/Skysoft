package com.skysoft.features.event.diana

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.hypixel.SkyBlockProfileId
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.WorldVec

internal object DianaBurrowStorage {
    private var loadedStorageKey: SkyBlockProfileId? = null
    private val persistentStorage get() = ProfileStorageApi.storage

    fun register() {
        DianaBurrowTargetTracker.setChangeListener { targets, now -> saveTargets(targets, now) }
    }

    fun restoreCurrentProfile(now: Long = System.currentTimeMillis()) {
        val storageKey = SkyBlockProfileApi.currentProfileId ?: return
        if (loadedStorageKey == storageKey) return
        loadedStorageKey = storageKey
        val targets = persistentTargets(now).map { target -> target.copy(updatedAtMillis = now) }
        DianaBurrowTargetTracker.restore(targets)
        DianaArrowGuess.restoreSequences(targets, now)
    }

    fun saveCurrentTargets(now: Long = System.currentTimeMillis()) {
        if (loadedStorageKey == null) return
        saveTargets(DianaBurrowTargetTracker.snapshot(), now, refreshTimestamp = true)
    }

    fun resetLoadedProfile() {
        loadedStorageKey = null
    }

    private fun saveTargets(
        targets: List<DianaBurrowTarget>,
        now: Long = System.currentTimeMillis(),
        refreshTimestamp: Boolean = false,
    ) {
        val storageKey = loadedStorageKey ?: SkyBlockProfileApi.currentProfileId ?: return
        val cachedTargets = targets
            .filter { target -> target.targetId > 0L }
            .sortedWith(compareBy({ it.location.x }, { it.location.y }, { it.location.z }, { it.type.name }))
        savePersistentTargets(storageKey, cachedTargets, now, refreshTimestamp)
    }

    private fun savePersistentTargets(
        storageKey: SkyBlockProfileId,
        targets: List<DianaBurrowTarget>,
        now: Long,
        refreshTimestamp: Boolean,
    ) {
        if (SkyBlockProfileApi.currentProfileId != storageKey) return
        val cache = persistentStorage.dianaBurrowCache
        val storageTargets = targets.map { target -> target.toStorageData() }
        if (cache.targets == storageTargets && (!refreshTimestamp || targets.isEmpty())) return
        ProfileStorageApi.updateProfile { profile ->
            val updatedCache = profile.dianaBurrowCache
            updatedCache.savedAtMillis = if (targets.isEmpty()) 0L else now
            updatedCache.targets.clear()
            updatedCache.targets += storageTargets
        }
    }

    private fun persistentTargets(now: Long): List<DianaBurrowTarget> {
        val cache = persistentStorage.dianaBurrowCache
        if (cache.targets.isEmpty()) {
            if (cache.savedAtMillis != 0L) {
                ProfileStorageApi.updateProfile { it.dianaBurrowCache.clear() }
            }
            return emptyList()
        }
        if (now - cache.savedAtMillis > RESTORE_WINDOW_MILLIS) {
            ProfileStorageApi.updateProfile { it.dianaBurrowCache.clear() }
            return emptyList()
        }
        val targets = cache.targets.mapNotNull { target -> target.toDianaTarget() }
        if (targets.isEmpty()) {
            ProfileStorageApi.updateProfile { it.dianaBurrowCache.clear() }
        }
        return targets
    }

    private const val RESTORE_WINDOW_MILLIS = 30 * 60 * 1_000L
}

private fun DianaBurrowTarget.toStorageData(): ProfileStorage.DianaBurrowTargetData =
    ProfileStorage.DianaBurrowTargetData(
        targetId = targetId,
        x = location.x,
        y = location.y,
        z = location.z,
        type = type.name,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        guessCandidates = guessCandidates.map { candidate ->
            ProfileStorage.DianaBurrowGuessCandidateData(
                x = candidate.x,
                y = candidate.y,
                z = candidate.z,
            )
        }.toMutableList(),
    )

private fun ProfileStorageView.DianaBurrowTargetData.toDianaTarget(): DianaBurrowTarget? {
    val burrowType = DianaBurrowType.entries.firstOrNull { type -> type.name == this.type } ?: return null
    val location = WorldVec(x, y, z).roundToBlock()
    val source = if (burrowType == DianaBurrowType.GUESS) DianaBurrowSource.GUESS else DianaBurrowSource.DETECTED
    return DianaBurrowTarget(
        targetId = targetId,
        location = location,
        type = burrowType,
        source = source,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        guessCandidates = if (source == DianaBurrowSource.GUESS) {
            (listOf(location) + guessCandidates.map { candidate -> WorldVec(candidate.x, candidate.y, candidate.z) })
                .map { candidate -> candidate.roundToBlock() }
                .distinctBy { candidate -> candidate.blockKey() }
        } else {
            emptyList()
        },
    )
}
