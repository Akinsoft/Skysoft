package com.skysoft.gui.tooltip

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.world.inventory.tooltip.TooltipComponent

interface SkysoftTooltipComponent : TooltipComponent {
    fun clientComponent(): ClientTooltipComponent
}
