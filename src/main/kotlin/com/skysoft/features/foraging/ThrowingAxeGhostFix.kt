package com.skysoft.features.foraging

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity

object ThrowingAxeGhostFix {
    @JvmStatic
    fun shouldHide(entity: Entity): Boolean {
        val display = entity as? Display.BlockDisplay ?: return false
        if (!SkysoftConfigGui.config().fixes.hideThrowingAxeGhostBlocks) return false
        val island = HypixelLocationState.currentIsland
        if (island != SkyBlockIsland.GALATEA && island != SkyBlockIsland.TORRHUS_CANYON) return false
        val state = display.blockRenderState()?.blockState() ?: return false
        return ThrowingAxeHelper.isTreeBlock(island, state)
    }
}
