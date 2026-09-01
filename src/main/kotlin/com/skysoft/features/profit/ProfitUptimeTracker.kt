package com.skysoft.features.profit

internal class ProfitUptimeTracker<T : Any>(
    private val pauseAfterMillis: (T) -> Int?,
    private val onUptimeChanged: (T, Long) -> Unit,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val lastActivityAtMillis = mutableMapOf<T, Long>()
    private val unconfirmedUptimeMillis = mutableMapOf<T, Long>()
    private val durationTicks = mutableMapOf<T, Int>()

    val hasUnconfirmedUptime: Boolean
        get() = unconfirmedUptimeMillis.isNotEmpty()

    fun lastActivityAt(target: T): Long? = lastActivityAtMillis[target]

    fun isPaused(preset: T, isWindowActive: Boolean): Boolean =
        !isWindowActive ||
            !isProfitTimerActive(lastActivityAtMillis[preset], currentTimeMillis(), pauseAfterMillis(preset))

    fun markActivity(preset: T) {
        val now = currentTimeMillis()
        if (isProfitTimerActive(lastActivityAtMillis[preset], now, pauseAfterMillis(preset))) {
            unconfirmedUptimeMillis.remove(preset)
        } else {
            rewind(preset)
        }
        lastActivityAtMillis[preset] = now
    }

    fun refreshActivity(preset: T) {
        val now = currentTimeMillis()
        if (!isProfitTimerActive(lastActivityAtMillis[preset], now, pauseAfterMillis(preset))) return
        unconfirmedUptimeMillis.remove(preset)
        lastActivityAtMillis[preset] = now
    }

    fun tick(preset: T?, isWindowActive: Boolean) {
        tick(listOfNotNull(preset), isWindowActive)
    }

    fun tick(activeTargets: Collection<T>, isWindowActive: Boolean) {
        val now = currentTimeMillis()
        unconfirmedUptimeMillis.keys.toList().forEach { trackedPreset ->
            val pauseAfter = pauseAfterMillis(trackedPreset)
            if (pauseAfter == null) {
                unconfirmedUptimeMillis.remove(trackedPreset)
            } else if (!isProfitTimerActive(lastActivityAtMillis[trackedPreset], now, pauseAfter)) {
                rewind(trackedPreset)
            }
        }
        durationTicks.keys.retainAll(activeTargets.toSet())
        activeTargets.forEach { target ->
            val pauseAfter = pauseAfterMillis(target)
            if (!isProfitTimerActive(lastActivityAtMillis[target], now, pauseAfter)) {
                durationTicks.remove(target)
                return@forEach
            }
            if (!isWindowActive) return@forEach
            val ticks = durationTicks.getOrDefault(target, 0) + 1
            if (ticks < DURATION_UPDATE_TICKS) {
                durationTicks[target] = ticks
                return@forEach
            }
            durationTicks.remove(target)
            if (pauseAfter != null) {
                unconfirmedUptimeMillis.merge(target, DURATION_UPDATE_MILLIS, Long::plus)
            }
            onUptimeChanged(target, DURATION_UPDATE_MILLIS)
        }
    }

    fun resetTickProgress() {
        durationTicks.clear()
    }

    fun clear() {
        lastActivityAtMillis.clear()
        unconfirmedUptimeMillis.clear()
        durationTicks.clear()
    }

    fun clear(target: T) {
        lastActivityAtMillis.remove(target)
        unconfirmedUptimeMillis.remove(target)
        durationTicks.remove(target)
    }

    private fun rewind(preset: T) {
        val uptimeMillis = unconfirmedUptimeMillis.remove(preset) ?: return
        onUptimeChanged(preset, -uptimeMillis)
    }
}

internal fun isProfitTimerActive(lastActivityAtMillis: Long?, now: Long, pauseAfterMillis: Int?): Boolean =
    lastActivityAtMillis != null && (now - lastActivityAtMillis).let { elapsed ->
        elapsed >= 0 && (pauseAfterMillis == null || elapsed <= pauseAfterMillis)
    }

private const val DURATION_UPDATE_TICKS = 20
private const val DURATION_UPDATE_MILLIS = 1_000L
