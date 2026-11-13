package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.gui.ScreenSlotLayout
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot

object InventoryCraftingHider {
    private val slotLayout = ScreenSlotLayout()

    @JvmStatic
    fun shouldHide(): Boolean =
        SkysoftConfigGui.config().gui.vanillaUi.isInventoryCraftingHidden && HypixelLocationState.inSkyBlock

    fun layoutScreen(screen: AbstractContainerScreen<*>) {
        if (screen !is InventoryScreen || !shouldHide()) {
            slotLayout.restore()
            return
        }
        val slots = screen.menu.slots
        moveSlot(screen, slots.getOrNull(InventoryMenu.RESULT_SLOT))
        for (index in InventoryMenu.CRAFT_SLOT_START until InventoryMenu.CRAFT_SLOT_END) {
            moveSlot(screen, slots.getOrNull(index))
        }
    }

    fun restoreScreen(screen: AbstractContainerScreen<*>) {
        slotLayout.restore(screen)
    }

    @JvmStatic
    fun cover(screen: InventoryScreen, context: GuiGraphicsExtractor) {
        layoutScreen(screen)
        if (!shouldHide()) return
        val accessor = screen as AbstractContainerScreenAccessor
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            AbstractContainerScreen.INVENTORY_LOCATION,
            accessor.skysoftGetLeftPos() + COVER_X,
            accessor.skysoftGetTopPos() + COVER_Y,
            PATCH_U,
            PATCH_V,
            COVER_WIDTH,
            COVER_HEIGHT,
            PATCH_SIZE,
            PATCH_SIZE,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
        )
    }

    private fun moveSlot(screen: AbstractContainerScreen<*>, slot: Slot?) {
        if (slot != null) slotLayout.move(screen, slot, HIDDEN_SLOT_COORD, HIDDEN_SLOT_COORD)
    }

    private const val COVER_X = 97
    private const val COVER_Y = 17
    private const val COVER_WIDTH = 74
    private const val COVER_HEIGHT = 36
    private const val PATCH_U = 88f
    private const val PATCH_V = 8f
    private const val PATCH_SIZE = 8
    private const val TEXTURE_SIZE = 256
    private const val HIDDEN_SLOT_COORD = -10_000
}
