package com.skysoft.features.event.diana

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileId
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.WorldVec

internal object DianaBurrowStorage {
    private val sessionTargets = mutableMapOf<SkyBlockProfileId, CachedDianaTargets>()
    private var loadedStorageKey: SkyBlockProfileId? = null
    private var storageKeyProvider: () -> SkyBlockProfileId? = { SkyBlockProfileApi.currentProfileId }
    private var persistentStorageProvider: () -> ProfileStorage.ProfileSpecific? = { ProfileStorageApi.storage }
    private var persistentDirtyMarker: () -> Unit = { ProfileStorageApi.markDirty() }
    private var persistentSaver: () -> Unit = { ProfileStorageApi.saveNow() }
    private var lastPersistentSaveMillis = Long.MIN_VALUE

    fun register() {
        DianaBurrowTargetTracker.setChangeListener { targets, now -> saveTargets(targets, now) }
    }

    fun restoreCurrentProfile(now: Long = System.currentTimeMillis()) {
        val storageKey = currentStorageKey() ?: return
        if (loadedStorageKey == storageKey) return
        loadedStorageKey = storageKey
        prune(now)

        val cachedTargets = listOfNotNull(
            sessionTargets[storageKey]?.takeIf { now - it.savedAtMillis <= RESTORE_WINDOW_MILLIS },
            persistentTargets(now),
        )
            .maxByOrNull { targets -> targets.savedAtMillis }
            ?.also { targets -> sessionTargets[storageKey] = targets }
            ?.targets
            .orEmpty()
            .map { target -> target.copy(updatedAtMillis = now) }

        DianaBurrowTargetTracker.restore(cachedTargets)
    }

    fun saveCurrentTargets(now: Long = System.currentTimeMillis()) {
        saveTargets(DianaBurrowTargetTracker.snapshot(), now, forcePersistentFlush = true)
    }

    fun refreshCurrentTargets(now: Long = System.currentTimeMillis()) {
        if (lastPersistentSaveMillis != Long.MIN_VALUE &&
            now - lastPersistentSaveMillis < PERSISTENT_REFRESH_INTERVAL_MILLIS
        ) {
            return
        }
        val targets = DianaBurrowTargetTracker.snapshot()
        if (targets.none { target -> target.source == DianaBurrowSource.DETECTED }) return
        saveTargets(targets, now, forcePersistentFlush = true)
    }

    fun resetLoadedProfile() {
        loadedStorageKey = null
        lastPersistentSaveMillis = Long.MIN_VALUE
    }

    private fun saveTargets(
        targets: List<DianaBurrowTarget>,
        now: Long = System.currentTimeMillis(),
        forcePersistentFlush: Boolean = false,
    ) {
        val storageKey = loadedStorageKey ?: currentStorageKey() ?: return
        val cachedTargets = targets
            .filter { target -> target.source == DianaBurrowSource.DETECTED && target.targetId > 0L }
            .sortedWith(compareBy({ it.location.x }, { it.location.y }, { it.location.z }, { it.type.name }))
        if (cachedTargets.isEmpty()) {
            sessionTargets.remove(storageKey)
            savePersistentTargets(storageKey, emptyList(), now, forcePersistentFlush)
            return
        }
        sessionTargets[storageKey] = CachedDianaTargets(cachedTargets, now)
        savePersistentTargets(storageKey, cachedTargets, now, forcePersistentFlush)
        prune(now)
    }

    private fun savePersistentTargets(
        storageKey: SkyBlockProfileId,
        targets: List<DianaBurrowTarget>,
        now: Long,
        forceFlush: Boolean,
    ) {
        if (currentStorageKey() != storageKey) return
        val cache = persistentStorageProvider()?.dianaBurrowCache ?: return
        val storageTargets = targets.map { target -> target.toStorageData() }
        if (cache.targets == storageTargets && (targets.isEmpty() || !forceFlush)) return
        cache.savedAtMillis = if (targets.isEmpty()) 0L else now
        cache.targets.clear()
        cache.targets += storageTargets
        persistentDirtyMarker()
        persistentSaver()
        lastPersistentSaveMillis = now
    }

    private fun persistentTargets(now: Long): CachedDianaTargets? {
        val cache = persistentStorageProvider()?.dianaBurrowCache ?: return null
        cache.repairLoadedValues()
        if (cache.targets.isEmpty() || now - cache.savedAtMillis > RESTORE_WINDOW_MILLIS) {
            if (cache.targets.isNotEmpty() || cache.savedAtMillis != 0L) {
                cache.clear()
                persistentDirtyMarker()
                persistentSaver()
                lastPersistentSaveMillis = now
            }
            return null
        }
        val targets = cache.targets.mapNotNull { target -> target.toDianaTarget() }
        if (targets.isEmpty()) {
            cache.clear()
            persistentDirtyMarker()
            persistentSaver()
            lastPersistentSaveMillis = now
            return null
        }
        return CachedDianaTargets(targets, cache.savedAtMillis)
    }

    private fun currentStorageKey(): SkyBlockProfileId? =
        storageKeyProvider()

    private fun prune(now: Long) {
        sessionTargets.entries.removeIf { (_, cachedTargets) -> now - cachedTargets.savedAtMillis > RESTORE_WINDOW_MILLIS }
    }

    private data class CachedDianaTargets(
        val targets: List<DianaBurrowTarget>,
        val savedAtMillis: Long,
    )

    private const val RESTORE_WINDOW_MILLIS = 5 * 60 * 1000L
    private const val PERSISTENT_REFRESH_INTERVAL_MILLIS = 30_000L
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
    )

private fun ProfileStorage.DianaBurrowTargetData.toDianaTarget(): DianaBurrowTarget? {
    val burrowType = DianaBurrowType.entries.firstOrNull { type -> type.name == this.type } ?: return null
    return DianaBurrowTarget(
        targetId = targetId,
        location = WorldVec(x, y, z).roundToBlock(),
        type = burrowType,
        source = DianaBurrowSource.DETECTED,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
