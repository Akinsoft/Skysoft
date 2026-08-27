package com.skysoft.features.misc.autosprint

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.misc.conditions.FeatureConditionState
import com.skysoft.mixin.ToggleKeyMappingAccessor
import com.skysoft.utils.SkysoftClientEvents
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.ToggleKeyMapping
import net.minecraft.client.player.LocalPlayer

object AutoSprint {
    private val conditions = FeatureConditionState()
    private var wasActive = false

    fun register() {
        conditions.startSession(config.settings.combinations)
        SkyBlockEventState.registerConsumer("Auto Sprint") { config.enabled }
        SkysoftClientEvents.onEndTick(
            "Auto Sprint tick",
            isActive = { config.enabled || wasActive },
        ) { minecraft ->
            val player = minecraft.player ?: return@onEndTick
            val isCurrentlyActive = isActive(player)
            if (wasActive && !isCurrentlyActive) {
                val sprintKey = minecraft.options.keySprint as ToggleKeyMapping
                (sprintKey as ToggleKeyMappingAccessor).skysoftReset()
                player.isSprinting = false
            }
            wasActive = isCurrentlyActive
        }
    }

    fun isActive(player: LocalPlayer): Boolean {
        if (!config.enabled) return false
        val settings = config.settings
        val combinations = settings.combinations
        if (combinations.isEmpty()) return true
        val heldItemId = player.mainHandItem.skyBlockId()
        return conditions.isActivationAllowed(combinations, heldItemId, settings.isConditionActivationReversed)
    }

    fun addHeldItem(source: FabricClientCommandSource): Int =
        conditions.addHeldItem(source, "Auto Sprint", config.settings.combinations)

    internal fun itemConditions() = conditions.itemConditions()

    internal fun markConditionsChanged() = conditions.markChanged()

    private val config
        get() = SkysoftConfigGui.config().misc.autoSprint
}
