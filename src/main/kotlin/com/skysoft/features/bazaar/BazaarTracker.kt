package com.skysoft.features.bazaar

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot

object BazaarTracker {
    fun register() = registerBazaarTracker()

    fun investmentPosition(productId: String): BazaarInvestmentPosition? =
        bazaarInvestmentPosition(storage.itemLots, productId)

    fun transactionsFor(productId: String, itemName: String, sinceMillis: Long) =
        bazaarTransactionsFor(storage, productId, itemName, sinceMillis)

    @JvmStatic
    fun handleMouseClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult =
        handleBazaarTrackerMouseClick(screen, click)

    @JvmStatic
    fun handleMouseButtonPress(button: Int): InputHandlingResult = handleBazaarTrackerMouseButtonPress(button)

    @JvmStatic
    fun renderSlotIndicatorBackground(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) =
        renderBazaarTrackerSlotIndicatorBackground(screen, context, slot)

    @JvmStatic
    fun renderSlotIndicatorOverlay(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) =
        renderBazaarTrackerSlotIndicatorOverlay(screen, context, slot)

    @JvmStatic
    fun layoutOrderMenu(screen: ContainerScreen): Int = layoutBazaarOrderMenu(screen)

    @JvmStatic
    fun restoreOrderMenu(screen: AbstractContainerScreen<*>) = restoreBazaarOrderMenu(screen)

    @JvmStatic
    fun shouldBlockOrderInteraction(screen: AbstractContainerScreen<*>, slotId: Int): Boolean =
        shouldBlockBazaarOrderInteraction(screen, slotId)
}

internal val config get() = SkysoftConfigGui.config().inventory.bazaar
internal val storage get() = ProfileStorageApi.storage.bazaarTracker
