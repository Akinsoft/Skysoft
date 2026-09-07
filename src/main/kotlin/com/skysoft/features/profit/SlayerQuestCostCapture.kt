package com.skysoft.features.profit

import kotlin.math.roundToLong

internal data class SlayerQuestCost(val currency: String, val amount: Long)

internal class SlayerQuestCostCapture(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private var questStartedAtMillis: Long? = null
    private var cost: SlayerQuestCost? = null

    fun questStarted() {
        val now = currentTimeMillis()
        if (questStartedAtMillis?.let { now - it in 0..QUEST_COST_CAPTURE_MILLIS } != true) cost = null
        questStartedAtMillis = now
    }

    fun recordChange(currency: String, change: Double) {
        val startedAt = questStartedAtMillis ?: return
        if (cost != null || change >= 0.0 || currentTimeMillis() - startedAt !in 0..QUEST_COST_CAPTURE_MILLIS) return
        recordCost(currency, (-change).roundToLong())
    }

    fun recordCost(currency: String, amount: Long) {
        if (amount <= 0L) return
        questStartedAtMillis = currentTimeMillis()
        cost = SlayerQuestCost(currency, amount)
    }

    fun take(): SlayerQuestCost? = cost?.also { clear() }

    fun clearExpired() {
        val startedAt = questStartedAtMillis ?: return
        if (cost == null && currentTimeMillis() - startedAt > QUEST_COST_CAPTURE_MILLIS) clear()
    }

    fun clear() {
        questStartedAtMillis = null
        cost = null
    }
}

private const val QUEST_COST_CAPTURE_MILLIS = 1_500L
