package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.world.entity.player.Player

object AbsorptionHeartLayout {
    fun resolveMaximumHealth(vanillaValue: Float, player: Player?): Float =
        SkysoftErrorBoundary.value("Absorption heart layout", vanillaValue) {
            (vanillaValue - absorptionSlotPoints(player)).coerceAtLeast(0.0f)
        }

    fun resolveVisibleHealth(vanillaValue: Int, player: Player?): Int =
        SkysoftErrorBoundary.value("Absorption heart layout", vanillaValue) {
            (vanillaValue - absorptionSlotPoints(player)).coerceAtLeast(0)
        }

    private fun absorptionSlotPoints(player: Player?): Int {
        if (!SkysoftConfigGui.config().gui.areAbsorptionHeartsMerged || player == null) return 0
        val absorptionPoints = kotlin.math.ceil(player.absorptionAmount).toInt()
        return ((absorptionPoints + 1) / 2) * 2
    }
}
