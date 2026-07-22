package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState

object VanillaRecipeBookHider {
    @JvmStatic
    fun shouldHideInInventory(): Boolean =
        SkysoftConfigGui.config().gui.vanillaUi.isVanillaRecipeBookHidden && HypixelLocationState.inSkyBlock
}
