package com.skysoft.features.inventory

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack

internal data class StoragePreviewTooltip(
    val items: List<ItemStack>,
    val columns: Int,
    val rows: Int,
) : TooltipComponent {
    init {
        require(columns > 0 && rows > 0) { "Storage preview dimensions must be positive" }
        require(items.size == columns * rows) { "Storage preview items do not match its dimensions" }
    }
}

internal class ClientStoragePreviewTooltip(
    private val preview: StoragePreviewTooltip,
) : ClientTooltipComponent {
    override fun getHeight(font: Font): Int = preview.rows * SLOT_SIZE + BOTTOM_PADDING

    override fun getWidth(font: Font): Int = preview.columns * SLOT_SIZE

    override fun extractImage(
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        context: GuiGraphicsExtractor,
    ) {
        preview.items.forEachIndexed { index, stack ->
            val slotX = x + index % preview.columns * SLOT_SIZE
            val slotY = y + index / preview.columns * SLOT_SIZE
            context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BORDER_COLOR)
            context.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, SLOT_COLOR)
            if (!stack.isEmpty) {
                context.item(stack, slotX + 1, slotY + 1)
                context.itemDecorations(font, stack, slotX + 1, slotY + 1)
            }
        }
    }

    private companion object {
        const val SLOT_SIZE = 18
        const val BOTTOM_PADDING = 2
        val SLOT_BORDER_COLOR = 0xFF555555.toInt()
        val SLOT_COLOR = 0xFF161616.toInt()
    }
}
