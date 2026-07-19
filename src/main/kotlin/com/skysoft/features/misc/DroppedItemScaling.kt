package com.skysoft.features.misc

import com.mojang.blaze3d.vertex.PoseStack
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemRarity
import net.minecraft.world.item.ItemStack

object DroppedItemScaling {
    fun isActive(): Boolean =
        SkysoftConfigGui.config().misc.droppedItemScaling.isEnabled && HypixelLocationState.inSkyBlock

    fun scaleFor(stack: ItemStack): Float {
        if (!isActive()) return DEFAULT_SCALE
        val rarity = SkyBlockItemRarity.from(stack) ?: return DEFAULT_SCALE
        return SkysoftConfigGui.config().misc.droppedItemScaling.settings.sizePercentFor(rarity) / PERCENT_SCALE
    }

    fun applyRenderScale(poseStack: PoseStack, scale: Float, modelMinY: Float) {
        if (scale == DEFAULT_SCALE) return
        poseStack.translate(0f, modelMinY * (1f - scale), 0f)
        poseStack.scale(scale, scale, scale)
    }

    private const val DEFAULT_SCALE = 1f
    private const val PERCENT_SCALE = 100f
}
