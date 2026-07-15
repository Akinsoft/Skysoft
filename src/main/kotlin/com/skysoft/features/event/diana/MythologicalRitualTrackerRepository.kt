package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.data.hypixel.SkyBlockProfileId
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.MayorPerkApi

internal object MythologicalRitualTrackerRepository {
    private val store = MythologicalRitualTrackerStore(SkysoftConfigFiles.mythologicalRitualTracker)
    private val sessionStats = mutableMapOf<SkyBlockProfileId, MythologicalRitualStats>()
    private var data = MythologicalRitualTrackerData()
    private var loaded = false
    private var lastKnownProfileId: SkyBlockProfileId? = null
    private var lastSaveAtMillis = 0L

    fun displayStateOrNull(): MythologicalRitualTrackerState? {
        ensureLoaded()
        val currentProfileId = SkyBlockProfileApi.currentProfileId
        if (currentProfileId != null) return writableState(currentProfileId)
        val profileId = lastKnownProfileId ?: return null
        val profile = data.profileOrNull(profileId.playerKey, profileId.profileKey) ?: return null
        val event = profile.eventForDisplay(currentEventKey(), UNRESOLVED_EVENT_KEY)
        return MythologicalRitualTrackerState(
            event = event,
            total = profile.total,
            session = sessionStats[profileId] ?: MythologicalRitualStats(),
            since = profile.since,
            magicFind = profile.magicFind,
        )
    }

    fun update(now: Long = System.currentTimeMillis(), action: (MythologicalRitualTrackerState) -> Unit) {
        val trackerState = writableStateOrNull() ?: return
        action(trackerState)
        saveNow(now)
    }

    fun recordActiveAt(now: Long) {
        val trackerState = writableStateOrNull() ?: return
        trackerState.event.recordActiveAt(now)
        trackerState.total.recordActiveAt(now)
        trackerState.session.recordActiveAt(now)
        if (now - lastSaveAtMillis >= ACTIVE_SAVE_INTERVAL_MILLIS) saveNow(now)
    }

    fun saveNow(now: Long = System.currentTimeMillis()) {
        if (!loaded) return
        store.save(data)
        lastSaveAtMillis = now
    }

    private fun ensureLoaded() {
        if (loaded) return
        data = store.load()
        loaded = true
    }

    private fun writableStateOrNull(): MythologicalRitualTrackerState? {
        ensureLoaded()
        val profileId = SkyBlockProfileApi.currentProfileId ?: return null
        return writableState(profileId)
    }

    private fun writableState(profileId: SkyBlockProfileId): MythologicalRitualTrackerState {
        lastKnownProfileId = profileId
        val eventKey = currentEventKey()
        val profile = data.profile(profileId.playerKey, profileId.profileKey)
        if (eventKey != UNRESOLVED_EVENT_KEY) {
            profile.mergeEvent(UNRESOLVED_EVENT_KEY, eventKey)
        }
        return MythologicalRitualTrackerState(
            event = profile.event(eventKey),
            total = profile.total,
            session = sessionStats.getOrPut(profileId) { MythologicalRitualStats() },
            since = profile.since,
            magicFind = profile.magicFind,
        )
    }

    private fun currentEventKey(): String =
        MayorPerkApi.mythologicalRitualEventKey ?: UNRESOLVED_EVENT_KEY

    private const val UNRESOLVED_EVENT_KEY = "unresolved-current-event"
    private const val ACTIVE_SAVE_INTERVAL_MILLIS = 30_000L
}
