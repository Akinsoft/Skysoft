package com.skysoft.features.profit

internal class ProfitUptimeTracker(
    private val pauseAfterMillis: (ProfitTrackerTarget) -> Int?,
    private val onUptimeChanged: (ProfitTrackerTarget, Long) -> Unit,
) {
    private val lastActivityAtMillis = mutableMapOf<ProfitTrackerTarget, Long>()
    private val unconfirmedUptimeMillis = mutableMapOf<ProfitTrackerTarget, Long>()
    private val durationTicks = mutableMapOf<ProfitTrackerTarget, Int>()

    val hasUnconfirmedUptime: Boolean
        get() = unconfirmedUptimeMillis.isNotEmpty()

    fun lastActivityAt(target: ProfitTrackerTarget): Long? = lastActivityAtMillis[target]

    fun isPaused(target: ProfitTrackerTarget, isWindowActive: Boolean): Boolean =
        !isWindowActive || !isActive(target, System.currentTimeMillis())

    fun markActivity(target: ProfitTrackerTarget) {
        val now = System.currentTimeMillis()
        if (isActive(target, now)) {
            unconfirmedUptimeMillis.remove(target)
        } else {
            rewind(target)
        }
        lastActivityAtMillis[target] = now
    }

    fun refreshActivity(target: ProfitTrackerTarget) {
        val now = System.currentTimeMillis()
        if (!isActive(target, now)) return
        unconfirmedUptimeMillis.remove(target)
        lastActivityAtMillis[target] = now
    }

    fun tick(activeTargets: Collection<ProfitTrackerTarget>, isWindowActive: Boolean) {
        val now = System.currentTimeMillis()
        unconfirmedUptimeMillis.keys.toList().forEach { trackedTarget ->
            if (pauseAfterMillis(trackedTarget) == null) {
                unconfirmedUptimeMillis.remove(trackedTarget)
            } else if (!isActive(trackedTarget, now)) {
                rewind(trackedTarget)
            }
        }
        durationTicks.keys.retainAll(activeTargets.toSet())
        activeTargets.forEach { target ->
            if (!isActive(target, now)) {
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
            if (pauseAfterMillis(target) != null) {
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

    fun clear(target: ProfitTrackerTarget) {
        lastActivityAtMillis.remove(target)
        unconfirmedUptimeMillis.remove(target)
        durationTicks.remove(target)
    }

    private fun isActive(target: ProfitTrackerTarget, now: Long): Boolean =
        isProfitTimerActive(lastActivityAtMillis[target], now, pauseAfterMillis(target))

    private fun rewind(target: ProfitTrackerTarget) {
        val uptimeMillis = unconfirmedUptimeMillis.remove(target) ?: return
        onUptimeChanged(target, -uptimeMillis)
    }
}

internal fun isProfitTimerActive(lastActivityAtMillis: Long?, now: Long, pauseAfterMillis: Int?): Boolean =
    lastActivityAtMillis != null && (now - lastActivityAtMillis).let { elapsed ->
        elapsed >= 0 && (pauseAfterMillis == null || elapsed <= pauseAfterMillis)
    }

private const val DURATION_UPDATE_TICKS = 20
private const val DURATION_UPDATE_MILLIS = 1_000L
