package com.skysoft.features.profit

import com.skysoft.config.CustomProfitTrackerConfig
import com.skysoft.config.ProfitTrackerConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockAreaState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.utils.MinecraftClient
import net.minecraft.client.Minecraft

internal data class ProfitTrackerTarget(
    val preset: ProfitTrackerPreset? = null,
    val customId: String? = null,
) {
    init {
        require((preset == null) != (customId == null))
    }

    val custom: CustomProfitTrackerConfig?
        get() = customId?.let { id -> customTrackers().firstOrNull { it.id == id } }

    val storageKey: String
        get() = preset?.name ?: requireNotNull(custom).storageKey

    val displayName: String
        get() = preset?.displayName ?: requireNotNull(custom).name

    val coinLabel: String
        get() = preset?.coinLabel ?: "Coins"

    val actionLabel: String
        get() = preset?.actionLabel ?: "Actions"

    val slayerType: SkyBlockSlayerType?
        get() = preset?.slayerType

    val config: ProfitTrackerConfig
        get() = preset?.let(::presetConfig) ?: requireNotNull(custom).config

    val isAvailable: Boolean
        get() = preset != null || custom != null

    companion object {
        fun preset(preset: ProfitTrackerPreset): ProfitTrackerTarget = ProfitTrackerTarget(preset = preset)

        fun custom(id: String): ProfitTrackerTarget = ProfitTrackerTarget(customId = id)
    }
}

internal fun customTrackerTargets(): List<ProfitTrackerTarget> =
    customTrackers().map { ProfitTrackerTarget.custom(it.id) }

internal fun ProfitTrackerTarget.isVisible(): Boolean = when {
    !isAvailable -> false
    preset != null -> ProfitTracker.isInPresetArea(preset)
    else -> custom?.matches(HypixelLocationState.currentIsland, SkyBlockAreaState.currentArea) == true
}

internal fun matchingCustomTrackerTargets(): List<ProfitTrackerTarget> =
    customTrackerTargets().filter(ProfitTrackerTarget::isVisible)

internal fun coinTrackingTargets(
    amount: Double,
    preset: ProfitTrackerPreset?,
    lastActivityAt: (ProfitTrackerTarget) -> Long?,
): List<ProfitTrackerTarget> {
    if (MinecraftClient.screen() != null || amount <= TALISMAN_OF_COINS_AMOUNT || amount >= MAXIMUM_COIN_GAIN) {
        return emptyList()
    }
    return buildList {
        preset?.let {
            val target = ProfitTrackerTarget.preset(it)
            if (shouldTrackCoinGain(it, lastActivityAt(target))) add(target)
        }
        addAll(matchingCustomTrackerTargets().filter { it.custom?.trackCoins == true })
    }
}

internal fun activeProfitTrackerTargets(locationPreset: ProfitTrackerPreset?): List<ProfitTrackerTarget> = buildList {
    locationPreset?.let { add(ProfitTrackerTarget.preset(it)) }
    addAll(matchingCustomTrackerTargets())
}

internal fun visibleProfitTrackerTargets(): List<ProfitTrackerTarget> = buildList {
    ProfitTracker.selectedPreset()?.takeIf(ProfitTracker::isInPresetArea)?.let {
        add(ProfitTrackerTarget.preset(it))
    }
    addAll(matchingCustomTrackerTargets())
}

private fun customTrackers(): List<CustomProfitTrackerConfig> =
    SkysoftConfigGui.config().profitTrackers.custom.trackers

private fun shouldTrackCoinGain(
    preset: ProfitTrackerPreset,
    lastActivityAtMillis: Long?,
): Boolean {
    if (preset != ProfitTrackerPreset.FARMING) {
        return preset.slayerType != null ||
            preset == ProfitTrackerPreset.FISHING ||
            preset == ProfitTrackerPreset.MYTHOLOGICAL_RITUAL
    }
    val recentlyFarmed = lastActivityAtMillis?.let {
        System.currentTimeMillis() - it <= BOUNTIFUL_ATTRIBUTION_MILLIS
    } == true
    val modifier = Minecraft.getInstance().player?.mainHandItem?.extraAttributes()?.getStringOrNull("modifier")
    return HypixelLocationState.currentIsland == SkyBlockIsland.GARDEN && recentlyFarmed && modifier == "bountiful"
}

private const val TALISMAN_OF_COINS_AMOUNT = 1.0
private const val MAXIMUM_COIN_GAIN = 100_000.0
private const val BOUNTIFUL_ATTRIBUTION_MILLIS = 2_000L
