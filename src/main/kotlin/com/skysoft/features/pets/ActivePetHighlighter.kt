package com.skysoft.features.pets

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot

object ActivePetHighlighter {
    private const val HIGHLIGHT_FILL_COLOR = 0x6030FF30
    private const val HIGHLIGHT_OUTLINE_COLOR = 0xFF30FF30.toInt()
    private const val HIGHLIGHT_INSET = 1
    private const val HIGHLIGHT_SIZE = 18
    private const val HIGHLIGHT_END_OFFSET = HIGHLIGHT_SIZE - HIGHLIGHT_INSET

    @JvmStatic
    fun renderBackground(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) {
        if (!PetStorageService.shouldHighlightActivePetSlot(screen.title.cleanSkyBlockText(), slot)) return
        context.fill(
            slot.x - HIGHLIGHT_INSET,
            slot.y - HIGHLIGHT_INSET,
            slot.x + HIGHLIGHT_END_OFFSET,
            slot.y + HIGHLIGHT_END_OFFSET,
            HIGHLIGHT_FILL_COLOR,
        )
    }

    fun renderOutline(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) {
        if (!PetStorageService.shouldHighlightActivePetSlot(screen.title.cleanSkyBlockText(), slot)) return
        context.outline(
            slot.x - HIGHLIGHT_INSET,
            slot.y - HIGHLIGHT_INSET,
            HIGHLIGHT_SIZE,
            HIGHLIGHT_SIZE,
            HIGHLIGHT_OUTLINE_COLOR,
        )
    }
}
