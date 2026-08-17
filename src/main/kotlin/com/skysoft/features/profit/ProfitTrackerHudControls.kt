package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.gui.OverlayControlCycle
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.animation.PanelFadeTransition
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

internal class ProfitTrackerHudControls(
    private val itemPanel: ProfitTrackerItemPanel,
) {
    private val resetTransition = PanelFadeTransition()
    private var pendingReset: ResetTarget? = null

    fun resetConfirmationOpacity(target: ProfitTrackerTarget, period: ProfitTrackingPeriod): Double {
        if (pendingReset == null) return 0.0
        if (pendingReset != ResetTarget(target, period)) {
            clearResetConfirmation()
            return 0.0
        }
        val opacity = resetTransition.opacity()
        if (!resetTransition.isVisible) pendingReset = null
        return opacity
    }

    fun isResetConfirmationPending(target: ProfitTrackerTarget, period: ProfitTrackingPeriod): Boolean =
        pendingReset == ResetTarget(target, period)

    fun isResetConfirmationInteractive(target: ProfitTrackerTarget, period: ProfitTrackingPeriod): Boolean =
        isResetConfirmationPending(target, period) && resetTransition.isInteractive

    fun clearResetConfirmation() {
        pendingReset = null
        resetTransition.reset()
    }

    fun wasClickHandled(
        screen: AbstractContainerScreen<*>,
        target: ProfitTrackerTarget,
        action: ProfitTrackerControl?,
        button: Int,
    ): Boolean {
        val activated = if (action == null) {
            wasInventoryItemAdded(screen, target, button)
        } else {
            wasActionHandled(target, action, button)
        }
        if (activated) SoundUtilities.playClickSound()
        return activated
    }

    fun wasKeyPressHandled(target: ProfitTrackerTarget, event: KeyEvent): Boolean =
        itemPanel.wasKeyPressHandled(event) { itemId -> selectSearchedItem(target, itemId) }

    fun wasCharTypedHandled(event: CharacterEvent): Boolean = itemPanel.wasCharTypedHandled(event)

    private fun wasInventoryItemAdded(
        screen: AbstractContainerScreen<*>,
        target: ProfitTrackerTarget,
        button: Int,
    ): Boolean {
        if (!itemPanel.isAddingFromInventory() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
        val player = Minecraft.getInstance().player ?: return false
        val itemId = slot?.takeIf { it.container === player.inventory }?.item?.skyBlockId() ?: return false
        if (itemId !in ProfitTracker.trackedItemIds(target)) ProfitTrackerItemCustomizations.addCustomItem(target, itemId)
        itemPanel.openItem(itemId)
        return true
    }

    private fun wasActionHandled(
        target: ProfitTrackerTarget,
        action: ProfitTrackerControl,
        button: Int,
    ): Boolean = when (action) {
        ProfitTrackerControl.Period -> wasPeriodCycled(target, button)
        ProfitTrackerControl.PriceSource -> wasTrackerPriceSourceCycled(target, button)
        ProfitTrackerControl.Reset -> wasLeftClickHandled(button) {
            pendingReset = ResetTarget(target, ProfitTracker.displayPeriod(target))
            resetTransition.show()
        }
        ProfitTrackerControl.CancelReset -> wasLeftClickHandled(button, resetTransition::hide)
        ProfitTrackerControl.ConfirmReset -> wasLeftClickHandled(button) {
            ProfitTracker.resetDisplayed(target)
            resetTransition.hide()
        }
        ProfitTrackerControl.More -> wasLeftClickHandled(button, itemPanel::toggleOverview)
        is ProfitTrackerControl.ManageItem -> wasLeftClickHandled(button) { itemPanel.toggleItem(action.itemId) }
        is ProfitTrackerControl.ItemPriceSource -> wasItemPriceSourceCycled(target, action.itemId, button)
        is ProfitTrackerControl.ExcludeItem -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.exclude(target, action.itemId)
            itemPanel.showOverview()
        }
        is ProfitTrackerControl.RestoreItem -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.restore(target, action.itemId)
        }
        is ProfitTrackerControl.RemoveCustomItem -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.removeCustomItem(target, action.itemId)
        }
        ProfitTrackerControl.AddItem -> wasLeftClickHandled(button, itemPanel::beginAddingItem)
        ProfitTrackerControl.AddItemInventory -> wasLeftClickHandled(button) {
            itemPanel.selectAddItemMode(AddItemMode.INVENTORY)
        }
        ProfitTrackerControl.AddItemSearch -> wasLeftClickHandled(button) {
            itemPanel.selectAddItemMode(AddItemMode.SEARCH)
        }
        is ProfitTrackerControl.AddItemSearchField -> wasLeftClickHandled(button) {
            itemPanel.focusSearch(action.localMouseX)
        }
        is ProfitTrackerControl.AddItemSearchResult -> wasLeftClickHandled(button) {
            selectSearchedItem(target, action.itemId)
        }
        ProfitTrackerControl.ResetCustomizations -> wasLeftClickHandled(button) {
            ProfitTrackerItemCustomizations.reset(target)
        }
    }

    private fun selectSearchedItem(target: ProfitTrackerTarget, itemId: String) {
        if (itemId in ProfitTracker.trackedItemIds(target)) {
            itemPanel.openItem(itemId)
        } else {
            ProfitTrackerItemCustomizations.addCustomItem(target, itemId)
        }
    }

    private fun wasPeriodCycled(target: ProfitTrackerTarget, button: Int): Boolean =
        OverlayControlCycle.wasClickHandled(button) { backwards ->
            ProfitTracker.cyclePeriod(target, backwards)
        }

    private fun wasTrackerPriceSourceCycled(target: ProfitTrackerTarget, button: Int): Boolean =
        OverlayControlCycle.wasClickHandled(button) { backwards ->
            val settings = target.config.settings
            settings.priceSource = nextProfitTrackerPriceSource(settings.priceSource, backwards)
            SkysoftConfigGui.config().saveNow()
        }

    private fun wasItemPriceSourceCycled(
        target: ProfitTrackerTarget,
        itemId: String,
        button: Int,
    ): Boolean = OverlayControlCycle.wasClickHandled(button) { backwards ->
        val choices = listOf(null) + ProfitTrackerPriceSource.entries
        val current = ProfitTrackerItemCustomizations.priceSourceOverride(target, itemId)
        ProfitTrackerItemCustomizations.setPriceSource(
            target,
            itemId,
            OverlayControlCycle.next(choices, current, backwards),
        )
    }

    private inline fun wasLeftClickHandled(button: Int, action: () -> Unit): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        action()
        return true
    }

    private data class ResetTarget(
        val target: ProfitTrackerTarget,
        val period: ProfitTrackingPeriod,
    )
}

internal fun profitTrackerScrollOffset(current: Int, verticalAmount: Double, maximumOffset: Int): Int =
    (current + if (verticalAmount < 0.0) 1 else -1).coerceIn(0, maximumOffset)

internal fun nextProfitTrackerPriceSource(
    current: ProfitTrackerPriceSource,
    backwards: Boolean,
): ProfitTrackerPriceSource {
    return OverlayControlCycle.next(ProfitTrackerPriceSource.entries, current, backwards)
}
