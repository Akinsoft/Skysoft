package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.mixin.AbstractContainerScreenAccessor
import kotlin.math.min
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot

object InventoryCraftingHider {
    @JvmStatic
    fun shouldHide(): Boolean =
        SkysoftConfigGui.config().gui.vanillaUi.isInventoryCraftingHidden && HypixelLocationState.inSkyBlock

    @JvmStatic
    fun shouldCancelClick(screen: AbstractContainerScreen<*>, slot: Slot?, slotId: Int): Boolean =
        shouldHide(screen) && isCraftingSlot(slot?.index ?: slotId)

    @JvmStatic
    fun shouldHideHighlight(screen: AbstractContainerScreen<*>, slot: Slot?): Boolean =
        slot != null && shouldHide(screen) && isCraftingSlot(slot.index)

    @JvmStatic
    fun cover(screen: InventoryScreen, context: GuiGraphicsExtractor) {
        if (!shouldHide()) return
        val accessor = screen as AbstractContainerScreenAccessor
        tileCover(context, accessor.skysoftGetLeftPos() + COVER_X, accessor.skysoftGetTopPos() + COVER_Y)
    }

    private fun shouldHide(screen: AbstractContainerScreen<*>): Boolean = screen is InventoryScreen && shouldHide()

    private fun isCraftingSlot(index: Int): Boolean =
        index == InventoryMenu.RESULT_SLOT ||
            index in InventoryMenu.CRAFT_SLOT_START until InventoryMenu.CRAFT_SLOT_END

    private fun tileCover(context: GuiGraphicsExtractor, originX: Int, originY: Int) {
        var remainingY = COVER_HEIGHT
        var destY = originY
        while (remainingY > 0) {
            val tileHeight = min(PATCH_SIZE, remainingY)
            var remainingX = COVER_WIDTH
            var destX = originX
            while (remainingX > 0) {
                val tileWidth = min(PATCH_SIZE, remainingX)
                blitPatch(context, destX, destY, tileWidth, tileHeight)
                destX += tileWidth
                remainingX -= tileWidth
            }
            destY += tileHeight
            remainingY -= tileHeight
        }
    }

    private fun blitPatch(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            AbstractContainerScreen.INVENTORY_LOCATION,
            x,
            y,
            PATCH_U,
            PATCH_V,
            width,
            height,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
        )
    }

    private const val COVER_X = 97
    private const val COVER_Y = 17
    private const val COVER_WIDTH = 74
    private const val COVER_HEIGHT = 36
    private const val PATCH_U = 88f
    private const val PATCH_V = 8f
    private const val PATCH_SIZE = 8
    private const val TEXTURE_SIZE = 256
}
