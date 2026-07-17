package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object InventoryEquipment {
    @JvmStatic
    fun register() {
        SkysoftClientEvents.onEndTick("Inventory Equipment tick") { tickInventoryEquipment() }
        SkysoftClientEvents.onDisconnect(
            "Inventory Equipment disconnect reset",
            ::resetInventoryEquipmentRuntimeState,
        )
        SkyBlockProfileApi.onProfileChange("Inventory Equipment profile reset") {
            resetInventoryEquipmentRuntimeState()
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

internal val inventoryEquipmentStorage: MutableList<ProfileStorage.SkyBlockStorageItemData>
    get() = ProfileStorageApi.storage.inventoryEquipment.also(::repairInventoryEquipmentItems)

internal fun cachedInventoryEquipmentStacks(): List<ItemStack> =
    inventoryEquipmentStorage.map { stackFor(it) }

internal fun isInventoryEquipmentAvailable(): Boolean =
    SkysoftConfigGui.config().inventory.isInventoryEquipmentEnabled && HypixelLocationState.inSkyBlock

internal fun resetInventoryEquipmentRuntimeState() {
    lastEquipmentInventoryKey = null
    restoreAllInventoryEquipmentSlotLayouts()
}

private fun tickInventoryEquipment() {
    if (!isInventoryEquipmentAvailable()) {
        lastEquipmentInventoryKey = null
        return
    }
    val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: run {
        lastEquipmentInventoryKey = null
        return
    }
    readInventoryEquipmentScreen(screen)
}
