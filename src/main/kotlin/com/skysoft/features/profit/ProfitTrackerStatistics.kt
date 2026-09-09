package com.skysoft.features.profit

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.features.event.diana.UNRESOLVED_MYTHOLOGICAL_RITUAL_EVENT_KEY
import java.time.LocalDate

internal class ProfitTrackerStatistics {
    private val sessionStats = mutableMapOf<String, ProfileStorage.ProfitTrackerStats>()

    fun stats(target: ProfitTrackerTarget): ProfileStorageView.ProfitTrackerStats = when (displayPeriod(target)) {
        ProfitTrackingPeriod.SESSION -> sessionStats[target.storageKey] ?: emptyProfitTrackerStats
        ProfitTrackingPeriod.TODAY -> with(ProfileStorageApi.storage.profitTracker) {
            today[target.storageKey].takeIf { todayEpochDay == LocalDate.now().toEpochDay() } ?: emptyProfitTrackerStats
        }
        ProfitTrackingPeriod.MAYOR -> requireNotNull(mythologicalRitualMayorStats(target))
        ProfitTrackingPeriod.TOTAL -> ProfileStorageApi.storage.profitTracker.totals[target.storageKey] ?: emptyProfitTrackerStats
    }

    fun displayPeriod(target: ProfitTrackerTarget): ProfitTrackingPeriod =
        ProfileStorageApi.storage.profitTracker.displayPeriods[target.storageKey]
            ?.let { period -> target.trackingPeriods.firstOrNull { it.name == period } }
            ?: if (target.preset == ProfitTrackerPreset.MYTHOLOGICAL_RITUAL) {
                ProfitTrackingPeriod.MAYOR
            } else {
                ProfitTrackingPeriod.SESSION
            }

    fun reset(target: ProfitTrackerTarget, period: ProfitTrackingPeriod) {
        when (period) {
            ProfitTrackingPeriod.SESSION -> sessionStats[target.storageKey]?.clear()
            ProfitTrackingPeriod.TODAY -> ProfileStorageApi.updateProfile { profile ->
                didRollProfitTrackerToday(profile.profitTracker, LocalDate.now().toEpochDay())
                profile.profitTracker.today[target.storageKey]?.clear()
            }
            ProfitTrackingPeriod.MAYOR -> ProfileStorageApi.updateProfile { profile ->
                mutableMythologicalRitualMayorStats(profile.profitTracker, target)?.clear()
            }
            ProfitTrackingPeriod.TOTAL -> ProfileStorageApi.updateProfile { profile ->
                profile.profitTracker.totals[target.storageKey]?.clear()
            }
        }
    }

    fun deleteCustomTracker(target: ProfitTrackerTarget) {
        require(target.custom != null)
        val key = target.storageKey
        sessionStats.remove(key)
        ProfileStorageApi.updateAll { storage ->
            val profiles = storage.profiles.values + storage.players.values.flatMap { it.profiles.values }
            profiles.forEach { profile ->
                with(profile.profitTracker) {
                    totals.remove(key)
                    today.remove(key)
                    displayPeriods.remove(key)
                    itemCustomizations.remove(key)
                }
            }
        }
    }

    fun update(target: ProfitTrackerTarget, action: (ProfileStorage.ProfitTrackerStats) -> Unit) {
        action(sessionStats.getOrPut(target.storageKey, ::newProfitTrackerStats))
        ProfileStorageApi.updateProfile { profile ->
            val tracker = profile.profitTracker
            didRollProfitTrackerToday(tracker, LocalDate.now().toEpochDay())
            action(tracker.today.getOrPut(target.storageKey, ::newProfitTrackerStats))
            mutableMythologicalRitualMayorStats(tracker, target)?.let(action)
            action(tracker.totals.getOrPut(target.storageKey, ::newProfitTrackerStats))
            target.preset?.let { tracker.lastPreset = it.name }
        }
    }

    fun synchronizePeriods(trackMayor: Boolean) {
        if (SkyBlockProfileApi.currentProfileKey == null) return
        val tracker = ProfileStorageApi.storage.profitTracker
        val today = LocalDate.now().toEpochDay()
        val updateMayor = trackMayor &&
            tracker.mythologicalRitualMayorKey != tracker.currentMythologicalRitualEventKey
        if (tracker.todayEpochDay == today && !updateMayor) return
        ProfileStorageApi.updateProfile { profile ->
            didRollProfitTrackerToday(profile.profitTracker, today)
            if (updateMayor) {
                mutableMythologicalRitualMayorStats(
                    profile.profitTracker,
                    ProfitTrackerTarget.preset(ProfitTrackerPreset.MYTHOLOGICAL_RITUAL),
                )
            }
        }
    }
}

private fun mythologicalRitualMayorStats(target: ProfitTrackerTarget): ProfileStorageView.ProfitTrackerStats? {
    if (target.preset != ProfitTrackerPreset.MYTHOLOGICAL_RITUAL) return null
    val tracker = ProfileStorageApi.storage.profitTracker
    return if (tracker.mythologicalRitualMayorKey == tracker.currentMythologicalRitualEventKey ||
        tracker.mythologicalRitualMayorKey == UNRESOLVED_MYTHOLOGICAL_RITUAL_EVENT_KEY
    ) tracker.mythologicalRitualMayor else emptyProfitTrackerStats
}

private fun mutableMythologicalRitualMayorStats(
    tracker: ProfileStorage.ProfitTrackerData,
    target: ProfitTrackerTarget,
): ProfileStorage.ProfitTrackerStats? {
    if (target.preset != ProfitTrackerPreset.MYTHOLOGICAL_RITUAL) return null
    val eventKey = tracker.currentMythologicalRitualEventKey
    if (tracker.mythologicalRitualMayorKey != eventKey) {
        val preserveStats = tracker.mythologicalRitualMayorKey == UNRESOLVED_MYTHOLOGICAL_RITUAL_EVENT_KEY
        tracker.mythologicalRitualMayorKey = eventKey
        if (!preserveStats) tracker.mythologicalRitualMayor.clear()
    }
    return tracker.mythologicalRitualMayor
}

private val ProfileStorageView.ProfitTrackerData.currentMythologicalRitualEventKey: String
    get() = MayorPerkApi.mythologicalRitualEventKey
        ?: mythologicalRitualMayorKey.takeIf(String::isNotBlank)
        ?: UNRESOLVED_MYTHOLOGICAL_RITUAL_EVENT_KEY

private val emptyProfitTrackerStats: ProfileStorageView.ProfitTrackerStats = ProfileStorage.ProfitTrackerStats()

private fun newProfitTrackerStats() = ProfileStorage.ProfitTrackerStats()

internal fun didRollProfitTrackerToday(tracker: ProfileStorage.ProfitTrackerData, epochDay: Long): Boolean {
    if (tracker.todayEpochDay == epochDay) return false
    tracker.todayEpochDay = epochDay
    tracker.today.clear()
    return true
}

enum class ProfitTrackingPeriod(val displayName: String) {
    SESSION("Session"),
    TODAY("Today"),
    MAYOR("Mayor"),
    TOTAL("Total"),
}
