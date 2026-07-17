package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState

object VanillaRecipeBookHider {
    @JvmStatic
    fun shouldHideInInventory(): Boolean =
        SkysoftConfigGui.config().inventory.isVanillaRecipeBookHidden && HypixelLocationState.inSkyBlock
}
