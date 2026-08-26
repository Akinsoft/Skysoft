package com.skysoft.features.event.diana

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileId
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.WorldVec

internal object DianaBurrowStorage {
    private var loadedStorageKey: SkyBlockProfileId? = null
    private var storageKeyProvider: () -> SkyBlockProfileId? = { SkyBlockProfileApi.currentProfileId }
    private var persistentStorageProvider: () -> ProfileStorage.ProfileSpecific? = { ProfileStorageApi.storage }
    private var persistentDirtyMarker: () -> Unit = { ProfileStorageApi.markDirty() }
    private var persistentSaver: () -> Unit = { ProfileStorageApi.saveNow() }

    fun register() {
        DianaBurrowTargetTracker.setChangeListener { targets, now -> saveTargets(targets, now) }
    }

    fun restoreCurrentProfile(now: Long = System.currentTimeMillis()) {
        val storageKey = currentStorageKey() ?: return
        if (loadedStorageKey == storageKey) return
        loadedStorageKey = storageKey
        DianaBurrowTargetTracker.restore(
            persistentTargets().map { target -> target.copy(updatedAtMillis = now) },
        )
    }

    fun saveCurrentTargets(now: Long = System.currentTimeMillis()) {
        if (loadedStorageKey == null) return
        saveTargets(DianaBurrowTargetTracker.snapshot(), now)
        persistentSaver()
    }

    fun resetLoadedProfile() {
        loadedStorageKey = null
    }

    private fun saveTargets(
        targets: List<DianaBurrowTarget>,
        now: Long = System.currentTimeMillis(),
    ) {
        val storageKey = loadedStorageKey ?: currentStorageKey() ?: return
        val cachedTargets = targets
            .filter { target -> target.source == DianaBurrowSource.DETECTED && target.targetId > 0L }
            .sortedWith(compareBy({ it.location.x }, { it.location.y }, { it.location.z }, { it.type.name }))
        savePersistentTargets(storageKey, cachedTargets, now)
    }

    private fun savePersistentTargets(
        storageKey: SkyBlockProfileId,
        targets: List<DianaBurrowTarget>,
        now: Long,
    ) {
        if (currentStorageKey() != storageKey) return
        val cache = persistentStorageProvider()?.dianaBurrowCache ?: return
        val storageTargets = targets.map { target -> target.toStorageData() }
        if (cache.targets == storageTargets) return
        cache.savedAtMillis = if (targets.isEmpty()) 0L else now
        cache.targets.clear()
        cache.targets += storageTargets
        persistentDirtyMarker()
    }

    private fun persistentTargets(): List<DianaBurrowTarget> {
        val cache = persistentStorageProvider()?.dianaBurrowCache ?: return emptyList()
        cache.repairLoadedValues()
        if (cache.targets.isEmpty()) {
            if (cache.savedAtMillis != 0L) {
                cache.clear()
                persistentDirtyMarker()
            }
            return emptyList()
        }
        val targets = cache.targets.mapNotNull { target -> target.toDianaTarget() }
        if (targets.isEmpty()) {
            cache.clear()
            persistentDirtyMarker()
        }
        return targets
    }

    private fun currentStorageKey(): SkyBlockProfileId? =
        storageKeyProvider()
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
