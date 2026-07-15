package com.skysoft.features.event.diana

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileId
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.ChangeResult

internal object DianaBurrowChainState {
    private val states = mutableListOf<ActiveDianaBurrowChain>()
    private var loadedStorageKey: SkyBlockProfileId? = null
    private var storageKeyProvider: () -> SkyBlockProfileId? = { SkyBlockProfileApi.currentProfileId }
    private var persistentStorageProvider: () -> ProfileStorage.ProfileSpecific? = { ProfileStorageApi.storage }
    private var persistentDirtyMarker: () -> Unit = { ProfileStorageApi.markDirty() }
    private var persistentSaver: () -> Unit = { ProfileStorageApi.saveNow() }

    fun restoreCurrentProfile(now: Long = System.currentTimeMillis()) {
        val storageKey = currentStorageKey() ?: return
        if (loadedStorageKey == storageKey) return
        loadedStorageKey = storageKey
        states.clear()
        states += persistentStates(now)
    }

    fun onProgress(
        progress: DianaBurrowProgress?,
        completedTarget: DianaBurrowTarget?,
        now: Long = System.currentTimeMillis(),
    ) {
        progress ?: return
        val changed = removeStateForTarget(completedTarget) == ChangeResult.CHANGED ||
            pruneExpiredStates(now) == ChangeResult.CHANGED
        if (changed) savePersistentStates(now)
    }

    fun onNextTargetAssigned(
        target: DianaBurrowTarget,
        progress: DianaBurrowProgress?,
        now: Long = System.currentTimeMillis(),
    ) {
        progress ?: return
        if (progress.isComplete) return
        pruneExpiredStates(now)
        states.removeAll { chain ->
            chain.nextTargetId == target.targetId || chain.sameBlockStartTargetId == target.targetId
        }
        states += ActiveDianaBurrowChain(
            completed = progress.completed,
            total = progress.total,
            nextTargetId = target.targetId,
            savedAtMillis = now,
        )
        savePersistentStates(now)
    }

    fun onTargetReplaced(
        previous: DianaBurrowTarget?,
        replacement: DianaBurrowTarget?,
        now: Long = System.currentTimeMillis(),
    ) {
        previous ?: return
        replacement ?: return
        if (previous.targetId == replacement.targetId) return
        pruneExpiredStates(now)
        var changed = false
        states.replaceAll { chain ->
            when {
                chain.nextTargetId == previous.targetId -> {
                    changed = true
                    chain.copy(nextTargetId = replacement.targetId, savedAtMillis = now)
                }
                chain.sameBlockStartTargetId == previous.targetId -> {
                    changed = true
                    chain.copy(sameBlockStartTargetId = replacement.targetId, savedAtMillis = now)
                }
                else -> chain
            }
        }
        if (changed) savePersistentStates(now)
    }

    fun onTreasureCompleted(target: DianaBurrowTarget?, now: Long = System.currentTimeMillis()): DianaBurrowTarget? {
        target ?: return null
        pruneExpiredStates(now)
        val state = stateForTarget(target) ?: return null
        if (!target.canCompleteTreasureFromReward()) return null
        if (state.isNextStepComplete) return null
        DianaRemovedBurrows.clearSuppression(target.location)
        DianaBurrowTargetTracker.addDetected(target.location, DianaBurrowType.START, now)
        val start = DianaBurrowTargetTracker.targetAt(target.location) ?: return null
        replaceState(
            previous = state,
            replacement = state.copy(sameBlockStartTargetId = start.targetId, savedAtMillis = now),
        )
        savePersistentStates(now)
        return start
    }

    fun clickProgress(target: DianaBurrowTarget, now: Long = System.currentTimeMillis()): DianaBurrowClickProgress? {
        if (target.source != DianaBurrowSource.DETECTED) return null
        pruneExpiredStates(now)
        val state = stateForTarget(target) ?: return null
        return when {
            target.type == DianaBurrowType.TREASURE &&
                target.targetId == state.nextTargetId &&
                !state.isNextStepComplete ->
                DianaBurrowClickProgress(completedClicks = 0, requiredClicks = 2)
            target.type == DianaBurrowType.START &&
                target.targetId == state.sameBlockStartTargetId ->
                DianaBurrowClickProgress(
                    completedClicks = 1,
                    requiredClicks = 2,
                    displayType = DianaBurrowType.TREASURE,
                )
            else -> null
        }
    }

    fun clear(persist: Boolean = false) {
        states.clear()
        if (persist) clearPersistentState()
    }

    fun resetLoadedProfile() {
        loadedStorageKey = null
        states.clear()
    }

    private fun stateForTarget(target: DianaBurrowTarget): ActiveDianaBurrowChain? =
        states.firstOrNull { chain ->
            chain.nextTargetId == target.targetId || chain.sameBlockStartTargetId == target.targetId
        }

    private fun removeStateForTarget(target: DianaBurrowTarget?): ChangeResult {
        target ?: return ChangeResult.UNCHANGED
        return ChangeResult.from(
            states.removeAll { chain ->
                chain.nextTargetId == target.targetId || chain.sameBlockStartTargetId == target.targetId
            },
        )
    }

    private fun replaceState(previous: ActiveDianaBurrowChain, replacement: ActiveDianaBurrowChain) {
        val index = states.indexOf(previous)
        if (index == -1) {
            states += replacement
        } else {
            states[index] = replacement
        }
    }

    private fun pruneExpiredStates(now: Long): ChangeResult =
        ChangeResult.from(states.removeAll { chain -> now - chain.savedAtMillis > CHAIN_RESTORE_WINDOW_MILLIS })

    private fun savePersistentStates(now: Long) {
        val restorableTargetIds = restorableTargetIds()
        val statesToSave = states.filter { chain -> chain.hasRestorableTarget(restorableTargetIds) }
        if (statesToSave.isEmpty()) {
            clearPersistentState()
            return
        }
        val data = persistentStorageProvider()?.dianaBurrowChain ?: return
        writePersistentStates(data, statesToSave, now)
        persistentDirtyMarker()
        persistentSaver()
    }

    private fun writePersistentStates(
        data: ProfileStorage.DianaBurrowChainData,
        statesToSave: List<ActiveDianaBurrowChain>,
        savedAtMillis: Long,
    ) {
        data.clear()
        data.savedAtMillis = savedAtMillis
        data.activeTargets += statesToSave.map { chain ->
            ProfileStorage.DianaBurrowChainTargetData(
                savedAtMillis = chain.savedAtMillis,
                completed = chain.completed,
                total = chain.total,
                nextTargetId = chain.nextTargetId,
                sameBlockStartTargetId = chain.sameBlockStartTargetId,
            )
        }
        val first = statesToSave.first()
        data.completed = first.completed
        data.total = first.total
        data.nextTargetId = first.nextTargetId
        data.sameBlockStartTargetId = first.sameBlockStartTargetId
    }

    private fun clearPersistentState() {
        val data = persistentStorageProvider()?.dianaBurrowChain ?: return
        if (data.savedAtMillis == 0L && !data.isUsable()) return
        data.clear()
        persistentDirtyMarker()
        persistentSaver()
    }

    private fun persistentStates(now: Long): List<ActiveDianaBurrowChain> {
        val data = persistentStorageProvider()?.dianaBurrowChain ?: return emptyList()
        data.repairLoadedValues()
        if (!data.isUsable()) return emptyList()

        val restorableTargetIds = restorableTargetIds()
        val activeTargets = data.activeTargets.toList()
        val restored = activeTargets
            .filter { target -> now - target.savedAtMillis <= CHAIN_RESTORE_WINDOW_MILLIS }
            .map { target ->
                ActiveDianaBurrowChain(
                    completed = target.completed,
                    total = target.total,
                    nextTargetId = target.nextTargetId,
                    sameBlockStartTargetId = target.sameBlockStartTargetId,
                    savedAtMillis = target.savedAtMillis,
                )
            }
            .filter { chain -> chain.hasRestorableTarget(restorableTargetIds) }

        if (restored.isEmpty()) {
            if (data.savedAtMillis != 0L) clearPersistentState()
            return emptyList()
        }
        if (restored.size != activeTargets.size) {
            writePersistentStates(data, restored, data.savedAtMillis.takeIf { savedAt -> savedAt > 0L } ?: now)
            persistentDirtyMarker()
            persistentSaver()
        }
        return restored
    }

    private fun restorableTargetIds(): Set<Long> =
        DianaBurrowTargetTracker.snapshot()
            .asSequence()
            .filter { target -> target.source == DianaBurrowSource.DETECTED }
            .mapTo(mutableSetOf()) { target -> target.targetId }

    private fun currentStorageKey(): SkyBlockProfileId? =
        storageKeyProvider()

    private const val CHAIN_RESTORE_WINDOW_MILLIS = 5 * 60 * 1000L
}

private data class ActiveDianaBurrowChain(
    val completed: Int,
    val total: Int,
    val nextTargetId: Long = 0L,
    val sameBlockStartTargetId: Long = 0L,
    val savedAtMillis: Long,
) {
    val isNextStepComplete: Boolean = completed + 1 >= total

    fun hasRestorableTarget(targetIds: Set<Long>): Boolean =
        nextTargetId in targetIds || sameBlockStartTargetId in targetIds
}

private fun DianaBurrowTarget.canCompleteTreasureFromReward(): Boolean =
    source == DianaBurrowSource.DETECTED && type == DianaBurrowType.TREASURE ||
        source == DianaBurrowSource.GUESS && type == DianaBurrowType.GUESS
