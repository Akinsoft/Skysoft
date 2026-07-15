package com.skysoft.features.event.diana

import com.google.gson.annotations.Expose

internal data class MythologicalRitualTrackerData(
    @Expose val players: MutableMap<String, MythologicalRitualPlayerData> = mutableMapOf(),
) {
    fun repairLoadedValues() {
        players.values.forEach { it.repairLoadedValues() }
    }

    fun profile(playerKey: String, profileKey: String): MythologicalRitualProfileData {
        val player = players.getOrPut(playerKey) { MythologicalRitualPlayerData() }
        return player.profiles.getOrPut(profileKey) { MythologicalRitualProfileData(profileName = profileKey) }
    }

    fun profileOrNull(playerKey: String, profileKey: String): MythologicalRitualProfileData? =
        players[playerKey]?.profiles?.get(profileKey)
}

internal data class MythologicalRitualPlayerData(
    @Expose val profiles: MutableMap<String, MythologicalRitualProfileData> = mutableMapOf(),
) {
    fun repairLoadedValues() {
        profiles.values.forEach { it.repairLoadedValues() }
    }
}

internal data class MythologicalRitualProfileData(
    @Expose var profileName: String = "",
    @Expose val events: MutableMap<String, MythologicalRitualStats> = mutableMapOf(),
    @Expose val total: MythologicalRitualStats = MythologicalRitualStats(),
    @Expose val since: MythologicalRitualSinceData = MythologicalRitualSinceData(),
    @Expose val magicFind: MythologicalRitualMagicFindData = MythologicalRitualMagicFindData(),
) {
    fun repairLoadedValues() {
        events.values.forEach { it.repairLoadedValues() }
        total.repairLoadedValues()
        since.repairLoadedValues()
        magicFind.repairLoadedValues()
    }

    fun event(eventKey: String): MythologicalRitualStats =
        events.getOrPut(eventKey) { MythologicalRitualStats() }

    fun eventForDisplay(eventKey: String, unresolvedEventKey: String): MythologicalRitualStats {
        val event = events[eventKey]
        val unresolved = events[unresolvedEventKey]
        if (eventKey == unresolvedEventKey || unresolved == null) return event ?: MythologicalRitualStats()
        return MythologicalRitualStats().also { display ->
            event?.let(display::mergeFrom)
            display.mergeFrom(unresolved)
        }
    }

    fun mergeEvent(sourceKey: String, targetKey: String) {
        if (sourceKey == targetKey) return
        val source = events.remove(sourceKey) ?: return
        val target = event(targetKey)
        target.mergeFrom(source)
    }
}

internal data class MythologicalRitualStats(
    @Expose val mobs: MutableMap<String, Long> = mutableMapOf(),
    @Expose val items: MutableMap<String, Long> = mutableMapOf(),
    @Expose var activeMillis: Long = 0L,
    @Expose var lastActiveAtMillis: Long = 0L,
) {
    fun repairLoadedValues() {
        mobs.removeInvalidCounts()
        items.removeInvalidCounts()
        activeMillis = activeMillis.coerceAtLeast(0L)
        lastActiveAtMillis = lastActiveAtMillis.coerceAtLeast(0L)
    }

    fun mob(key: String): Long = mobs[key] ?: 0L

    fun item(key: String): Long = items[key] ?: 0L

    fun addMob(key: String, amount: Long = 1L) {
        mobs.addPositive(key, amount)
    }

    fun addItem(key: String, amount: Long = 1L) {
        items.addPositive(key, amount)
    }

    fun recordActiveAt(now: Long) {
        if (lastActiveAtMillis > 0L) {
            activeMillis += (now - lastActiveAtMillis).coerceIn(0L, ACTIVE_TIME_STEP_CAP_MILLIS)
        }
        lastActiveAtMillis = now
    }

    fun mergeFrom(other: MythologicalRitualStats) {
        other.mobs.forEach { (key, value) -> mobs.addPositive(key, value) }
        other.items.forEach { (key, value) -> items.addPositive(key, value) }
        activeMillis += other.activeMillis.coerceAtLeast(0L)
        lastActiveAtMillis = maxOf(lastActiveAtMillis, other.lastActiveAtMillis)
        repairLoadedValues()
    }
}

internal data class MythologicalRitualSinceData(
    @Expose var mobsSinceInq: Long = 0L,
    @Expose var mobsSinceKing: Long = 0L,
    @Expose var mobsSinceManti: Long = 0L,
    @Expose var mobsSinceSphinx: Long = 0L,
    @Expose var inqsSinceChim: Long = 0L,
    @Expose var inqsSinceLsChim: Long = 0L,
    @Expose var minotaursSinceStick: Long = 0L,
    @Expose var champsSinceRelic: Long = 0L,
    @Expose var mantiSinceCore: Long = 0L,
    @Expose var mantiSinceLsCore: Long = 0L,
    @Expose var mantiSinceStinger: Long = 0L,
    @Expose var mantiSinceLsStinger: Long = 0L,
    @Expose var kingSinceWool: Long = 0L,
    @Expose var kingSinceLsWool: Long = 0L,
    @Expose var sphinxSinceFood: Long = 0L,
    @Expose var sphinxSinceLsFood: Long = 0L,
) {
    fun repairLoadedValues() {
        mobsSinceInq = mobsSinceInq.coerceAtLeast(0L)
        mobsSinceKing = mobsSinceKing.coerceAtLeast(0L)
        mobsSinceManti = mobsSinceManti.coerceAtLeast(0L)
        mobsSinceSphinx = mobsSinceSphinx.coerceAtLeast(0L)
        inqsSinceChim = inqsSinceChim.coerceAtLeast(0L)
        inqsSinceLsChim = inqsSinceLsChim.coerceAtLeast(0L)
        minotaursSinceStick = minotaursSinceStick.coerceAtLeast(0L)
        champsSinceRelic = champsSinceRelic.coerceAtLeast(0L)
        mantiSinceCore = mantiSinceCore.coerceAtLeast(0L)
        mantiSinceLsCore = mantiSinceLsCore.coerceAtLeast(0L)
        mantiSinceStinger = mantiSinceStinger.coerceAtLeast(0L)
        mantiSinceLsStinger = mantiSinceLsStinger.coerceAtLeast(0L)
        kingSinceWool = kingSinceWool.coerceAtLeast(0L)
        kingSinceLsWool = kingSinceLsWool.coerceAtLeast(0L)
        sphinxSinceFood = sphinxSinceFood.coerceAtLeast(0L)
        sphinxSinceLsFood = sphinxSinceLsFood.coerceAtLeast(0L)
    }
}

internal data class MythologicalRitualMagicFindData(
    @Expose val highest: MutableMap<String, Int> = mutableMapOf(),
) {
    fun repairLoadedValues() {
        highest.entries.removeIf { entry -> entry.key.isBlank() || entry.value < 0 }
    }

    fun get(key: String): Int = highest[key] ?: 0

    fun record(key: String, value: Int) {
        if (value > get(key)) highest[key] = value
    }
}

internal data class MythologicalRitualTrackerState(
    val event: MythologicalRitualStats,
    val total: MythologicalRitualStats,
    val session: MythologicalRitualStats,
    val since: MythologicalRitualSinceData,
    val magicFind: MythologicalRitualMagicFindData,
)

private fun MutableMap<String, Long>.addPositive(key: String, amount: Long) {
    if (key.isBlank() || amount <= 0L) return
    this[key] = (this[key] ?: 0L) + amount
}

private fun MutableMap<String, Long>.removeInvalidCounts() {
    entries.removeIf { entry -> entry.key.isBlank() || entry.value <= 0L }
}

private const val ACTIVE_TIME_STEP_CAP_MILLIS = 60_000L
