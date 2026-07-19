package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object MinisterCalendarTooltip {
    private const val CALENDAR_TITLE = "Calendar and Events"
    private const val MAYOR_SLOT = 37
    private const val DESCRIPTION_WIDTH = 170
    private val mayorNamePattern = Regex("Mayor .+")

    fun register() {
        MayorPerkApi.registerConsumer("Minister in Calendar", ::isDemandActive)
    }

    fun prepare(
        screen: AbstractContainerScreen<*>,
        context: GuiGraphicsExtractor,
    ) {
        if (!isConfigured() || screen.title.cleanSkyBlockText() != CALENDAR_TITLE) return
        val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot() ?: return
        if (slot.containerSlot != MAYOR_SLOT || !mayorNamePattern.matches(slot.item.hoverName.cleanSkyBlockText())) return
        if (!screen.menu.carried.isEmpty) return
        val minister = MayorPerkApi.currentMinister ?: return
        val font = Minecraft.getInstance().font
        val lines = buildList {
            add(LegacyTextRenderer.formattedSequence("§dMinister ${minister.name}"))
            add(LegacyTextRenderer.formattedSequence("§e${minister.perk.name}"))
            LegacyTextRenderer.wrap(font, "§7${minister.perk.description}", DESCRIPTION_WIDTH).forEach { line ->
                add(LegacyTextRenderer.formattedSequence(line))
            }
        }
        AdjacentTooltipRenderer.prepare(context, lines)
    }

    private fun isDemandActive(): Boolean = isConfigured()

    private fun isConfigured(): Boolean =
        SkysoftConfigGui.config().inventory.isMinisterInCalendarShown && HypixelLocationState.inSkyBlock
}
