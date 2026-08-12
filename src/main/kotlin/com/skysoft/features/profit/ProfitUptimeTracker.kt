package com.skysoft.features.profit

internal class ProfitUptimeTracker(
    private val pauseAfterMillis: (ProfitTrackerPreset) -> Int,
    private val onUptimeChanged: (ProfitTrackerPreset, Long) -> Unit,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val lastActivityAtMillis = mutableMapOf<ProfitTrackerPreset, Long>()
    private val unconfirmedUptimeMillis = mutableMapOf<ProfitTrackerPreset, Long>()
    private var durationTicks = 0

    val hasUnconfirmedUptime: Boolean
        get() = unconfirmedUptimeMillis.isNotEmpty()

    fun lastActivityAt(preset: ProfitTrackerPreset): Long? = lastActivityAtMillis[preset]

    fun isPaused(preset: ProfitTrackerPreset, isWindowActive: Boolean): Boolean =
        !isWindowActive ||
            !isProfitTimerActive(lastActivityAtMillis[preset], currentTimeMillis(), pauseAfterMillis(preset))

    fun markActivity(preset: ProfitTrackerPreset) {
        val now = currentTimeMillis()
        if (isProfitTimerActive(lastActivityAtMillis[preset], now, pauseAfterMillis(preset))) {
            unconfirmedUptimeMillis.remove(preset)
        } else {
            rewind(preset)
        }
        lastActivityAtMillis[preset] = now
    }

    fun refreshActivity(preset: ProfitTrackerPreset) {
        val now = currentTimeMillis()
        if (!isProfitTimerActive(lastActivityAtMillis[preset], now, pauseAfterMillis(preset))) return
        unconfirmedUptimeMillis.remove(preset)
        lastActivityAtMillis[preset] = now
    }

    fun tick(preset: ProfitTrackerPreset?, isWindowActive: Boolean) {
        val now = currentTimeMillis()
        unconfirmedUptimeMillis.keys
            .filter { trackedPreset ->
                !isProfitTimerActive(lastActivityAtMillis[trackedPreset], now, pauseAfterMillis(trackedPreset))
            }
            .forEach(::rewind)
        if (preset == null) return
        if (!isProfitTimerActive(lastActivityAtMillis[preset], now, pauseAfterMillis(preset))) {
            durationTicks = 0
            return
        }
        if (!isWindowActive || ++durationTicks < DURATION_UPDATE_TICKS) return
        durationTicks = 0
        unconfirmedUptimeMillis.merge(preset, DURATION_UPDATE_MILLIS, Long::plus)
        onUptimeChanged(preset, DURATION_UPDATE_MILLIS)
    }

    fun resetTickProgress() {
        durationTicks = 0
    }

    fun clear() {
        lastActivityAtMillis.clear()
        unconfirmedUptimeMillis.clear()
        durationTicks = 0
    }

    private fun rewind(preset: ProfitTrackerPreset) {
        val uptimeMillis = unconfirmedUptimeMillis.remove(preset) ?: return
        onUptimeChanged(preset, -uptimeMillis)
    }
}

internal fun isProfitTimerActive(lastActivityAtMillis: Long?, now: Long, pauseAfterMillis: Int): Boolean =
    lastActivityAtMillis != null && now - lastActivityAtMillis in 0..pauseAfterMillis.toLong()

private const val DURATION_UPDATE_TICKS = 20
private const val DURATION_UPDATE_MILLIS = 1_000L
