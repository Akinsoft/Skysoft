package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal fun drawItemListSettingsButton(
    context: GuiGraphicsExtractor,
    bounds: Rect,
    hovered: Boolean,
    alpha: Double = 1.0,
) {
    PixelButtonRenderer.draw(
        context,
        Minecraft.getInstance().font,
        bounds,
        "",
        selected = false,
        hovered = hovered,
        enabled = true,
        alpha = alpha,
    )
    context.item(
        ItemStack(Items.REDSTONE_TORCH),
        bounds.x + (bounds.width - SETTINGS_ICON_SIZE) / 2,
        bounds.y + (bounds.height - SETTINGS_ICON_SIZE) / 2,
    )
    if (alpha < 1.0) {
        context.fill(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            SETTINGS_ICON_DIM.withScaledAlpha(1.0 - alpha),
        )
    }
}

private const val SETTINGS_ICON_SIZE = 16
private val SETTINGS_ICON_DIM = 0xB0000000.toInt()
