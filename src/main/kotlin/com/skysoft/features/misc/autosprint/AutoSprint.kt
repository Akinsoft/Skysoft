package com.skysoft.features.misc.autosprint

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.misc.conditions.FeatureConditionActivationCache
import com.skysoft.features.misc.conditions.FeatureConditionActivationKey
import com.skysoft.features.misc.conditions.FeatureConditionContext
import com.skysoft.features.misc.conditions.FeatureConditionVersion
import com.skysoft.features.misc.conditions.FeatureConditions
import com.skysoft.features.misc.conditions.FeatureItemConditionCatalogue
import com.skysoft.features.misc.conditions.FeatureItemConditionCommand
import com.skysoft.mixin.ToggleKeyMappingAccessor
import com.skysoft.utils.SkysoftClientEvents
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.ToggleKeyMapping
import net.minecraft.client.player.LocalPlayer

object AutoSprint {
    private val activationCache = FeatureConditionActivationCache()
    private val conditionVersion = FeatureConditionVersion()
    private val itemCatalogue = FeatureItemConditionCatalogue()
    private var wasActive = false
    internal var lastActivationChange = "none"
        private set
    internal var lastResetOutcome = "none"
        private set

    fun register() {
        itemCatalogue.startSession(config.settings.combinations)
        SkysoftClientEvents.onEndTick("Auto Sprint tick") { minecraft ->
            val player = minecraft.player ?: return@onEndTick
            val isCurrentlyActive = isActive(player)
            if (wasActive && !isCurrentlyActive) {
                val sprintKey = minecraft.options.keySprint as ToggleKeyMapping
                val wasSprinting = player.isSprinting
                val wasSprintKeyDown = sprintKey.isDown
                val wasRestorePending = sprintKey.shouldRestoreStateOnScreenClosed()
                (sprintKey as ToggleKeyMappingAccessor).skysoftReset()
                player.isSprinting = false
                lastResetOutcome =
                    "sprinting=$wasSprinting->${player.isSprinting} keyDown=$wasSprintKeyDown->${sprintKey.isDown} " +
                    "restorePending=$wasRestorePending->false"
            }
            if (isCurrentlyActive != wasActive) lastActivationChange = "$wasActive->$isCurrentlyActive"
            wasActive = isCurrentlyActive
        }
    }

    fun isActive(player: LocalPlayer): Boolean {
        if (!config.enabled) return false
        val settings = config.settings
        val combinations = settings.combinations
        if (combinations.isEmpty()) return true
        val heldItemId = player.mainHandItem.skyBlockId()
        return activationCache.isActivationAllowed(
            FeatureConditionActivationKey(
                locationVersion = HypixelLocationState.locationVersion,
                eventVersion = SkyBlockEventState.version,
                rulesVersion = conditionVersion.version,
                heldItemId = heldItemId,
                isConditionActivationReversed = settings.isConditionActivationReversed,
            ),
        ) {
            FeatureConditions.isActivationAllowed(
                combinations,
                FeatureConditionContext(
                    isInSkyBlock = HypixelLocationState.inSkyBlock,
                    island = HypixelLocationState.currentIsland,
                    activeEvents = SkyBlockEventState.activeEvents(),
                    heldItemId = heldItemId,
                ),
                isConditionActivationReversed = settings.isConditionActivationReversed,
            )
        }
    }

    fun addHeldItem(source: FabricClientCommandSource): Int = FeatureItemConditionCommand.addHeldItem(
        source = source,
        featureName = "Auto Sprint",
        combinations = config.settings.combinations,
        catalogue = itemCatalogue,
        onChanged = conditionVersion::markChanged,
    )

    internal fun itemConditions() = itemCatalogue.conditions()

    internal fun markConditionsChanged() = conditionVersion.markChanged()

    private val config
        get() = SkysoftConfigGui.config().misc.autoSprint
}
