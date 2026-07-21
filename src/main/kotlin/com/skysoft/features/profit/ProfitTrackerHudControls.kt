package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.SoundUtilities
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW

internal class ProfitTrackerHudControls(
    private val itemPanel: ProfitTrackerItemPanel,
) {
    fun wasClickHandled(
        screen: AbstractContainerScreen<*>,
        action: ProfitTrackerControl?,
        button: Int,
    ): Boolean {
        val preset = ProfitTracker.selectedPreset() ?: return false
        val activated = if (action == null) {
            wasInventoryItemAdded(screen, preset, button)
        } else {
            wasActionHandled(preset, action, button)
        }
        if (activated) SoundUtilities.playClickSound()
        return activated
    }

    private fun wasInventoryItemAdded(
        screen: AbstractContainerScreen<*>,
        preset: ProfitTrackerPreset,
        button: Int,
    ): Boolean {
        if (!itemPanel.isAddingItem() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
        val player = Minecraft.getInstance().player ?: return false
        val itemId = slot?.takeIf { it.container === player.inventory }?.item?.skyBlockId() ?: return false
        if (itemId !in ProfitTracker.trackedItemIds(preset)) ProfitTrackerItemCustomizations.addCustomItem(preset, itemId)
        itemPanel.openItem(itemId)
        return true
    }

    private fun wasActionHandled(
        preset: ProfitTrackerPreset,
        action: ProfitTrackerControl,
        button: Int,
    ): Boolean = when (action) {
        ProfitTrackerControl.Period -> wasPeriodCycled(preset, button)
        ProfitTrackerControl.PriceSource -> wasTrackerPriceSourceCycled(preset, button)
        ProfitTrackerControl.Reset -> wasLeftClickHandled(button) { ProfitTracker.resetDisplayed(preset) }
        ProfitTrackerControl.More -> wasLeftClickHandled(button, itemPanel::toggleOverview)
        is ProfitTrackerControl.ManageItem -> wasLeftClickHandled(button) { itemPanel.toggleItem(action.itemId) }
        is ProfitTrackerControl.ItemPriceSource -> wasItemPriceSourceCycled(preset, action.itemId, button)
        is ProfitTrackerControl.ExcludeItem -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.exclude(preset, action.itemId)
            itemPanel.showOverview()
        }
        is ProfitTrackerControl.RestoreItem -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.restore(preset, action.itemId)
        }
        is ProfitTrackerControl.RemoveCustomItem -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.removeCustomItem(preset, action.itemId)
        }
        ProfitTrackerControl.AddItem -> wasLeftClickHandled(button, itemPanel::beginAddingItem)
        ProfitTrackerControl.ResetCustomizations -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.reset(preset)
        }
    }

    private fun wasPeriodCycled(preset: ProfitTrackerPreset, button: Int): Boolean =
        wasCycleClickHandled(button) { backwards ->
            ProfitTracker.cyclePeriod(preset, backwards)
        }

    private fun wasTrackerPriceSourceCycled(preset: ProfitTrackerPreset, button: Int): Boolean =
        wasCycleClickHandled(button) { backwards ->
            val settings = presetConfig(preset).settings
            settings.priceSource = nextProfitTrackerPriceSource(settings.priceSource, backwards)
            SkysoftConfigGui.config().saveNow()
        }

    private fun wasItemPriceSourceCycled(
        preset: ProfitTrackerPreset,
        itemId: String,
        button: Int,
    ): Boolean = wasCycleClickHandled(button) { backwards ->
        val choices = listOf(null) + ProfitTrackerPriceSource.entries
        val current = ProfitTrackerItemCustomizations.priceSourceOverride(preset, itemId)
        val step = if (backwards) -1 else 1
        val next = choices[Math.floorMod(choices.indexOf(current) + step, choices.size)]
        ProfitTrackerItemCustomizations.setPriceSource(preset, itemId, next)
    }

    private inline fun wasLeftClickHandled(button: Int, action: () -> Unit): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        action()
        return true
    }

    private inline fun wasCycleClickHandled(button: Int, action: (Boolean) -> Unit): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
        action(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        return true
    }
}
