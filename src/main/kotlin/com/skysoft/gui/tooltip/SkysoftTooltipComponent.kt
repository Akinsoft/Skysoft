package com.skysoft.gui.tooltip

import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.world.inventory.tooltip.TooltipComponent

interface SkysoftTooltipComponent : TooltipComponent {
    fun clientComponent(): ClientTooltipComponent
}

object SkysoftTooltipComponents {
    fun register() {
        ClientTooltipComponentCallback.EVENT.register { component ->
            (component as? SkysoftTooltipComponent)?.clientComponent()
        }
    }
}
