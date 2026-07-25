package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ShortbowCooldownHider {
    @JvmStatic
    fun shouldHide(stack: ItemStack): Boolean {
        if (!SkysoftConfigGui.config().gui.vanillaUi.areShortbowCooldownsHidden) return false
        if (!HypixelLocationState.inSkyBlock || stack.item != Items.BOW) return false
        return stack.get(DataComponents.LORE)
            ?.lines()
            ?.any { line -> line.string.contains(SHORTBOW_LORE_MARKER) }
            ?: false
    }

    private const val SHORTBOW_LORE_MARKER = "Shortbow: Instantly shoots!"
}
