package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot

object InventoryEquipment {
    @JvmStatic
    fun register() {
        InventoryEquipmentCache.registerConsumer("Inventory screen") { inventoryEquipmentConfig.enabled }
        SkysoftClientEvents.onDisconnect(
            "Inventory Equipment screen reset",
            ::restoreAllInventoryEquipmentSlotLayouts,
        )
        SkyBlockProfileApi.onProfileChange("Inventory Equipment screen profile reset", { inventoryEquipmentConfig.enabled }) {
            restoreAllInventoryEquipmentSlotLayouts()
        }
    }

    @JvmStatic
    fun layoutScreen(screen: AbstractContainerScreen<*>) = updateInventoryEquipmentSlotLayout(screen)

    @JvmStatic
    fun restoreScreen(screen: AbstractContainerScreen<*>) = restoreInventoryEquipmentSlotLayout(screen)

    @JvmStatic
    fun renderBackground(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor) =
        renderInventoryEquipmentBackground(screen, context)

    @JvmStatic
    fun render(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) =
        renderInventoryEquipment(screen, context, mouseX, mouseY)

    @JvmStatic
    fun handleMouseClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult =
        handleInventoryEquipmentMouseClick(screen, click)

    @JvmStatic
    fun isEquipmentSlot(slot: Slot?): Boolean = isInventoryEquipmentSlot(slot)
}

internal val inventoryEquipmentConfig
    get() = SkysoftConfigGui.config().inventory.inventoryEquipment

internal fun isInventoryEquipmentAvailable(): Boolean =
    inventoryEquipmentConfig.enabled && HypixelLocationState.inSkyBlock
