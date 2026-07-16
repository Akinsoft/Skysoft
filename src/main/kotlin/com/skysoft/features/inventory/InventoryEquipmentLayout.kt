package com.skysoft.features.inventory

import com.skysoft.SkysoftMod
import com.skysoft.data.ProfileStorage
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import java.util.IdentityHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

private val movedOffhandSlots = IdentityHashMap<AbstractContainerScreen<*>, MovedSlot>()
private var warnedMissingOffhandSlot = false

internal fun updateInventoryEquipmentSlotLayout(screen: AbstractContainerScreen<*>) {
    if (!shouldShowInventoryEquipment(screen)) {
        restoreInventoryEquipmentSlotLayout(screen)
        return
    }

    val offhandSlot = screen.menu.slots.getOrNull(InventoryEquipmentLayout.OFFHAND_SLOT_INDEX) ?: run {
        warnMissingOffhandSlot()
        return
    }
    val original = movedOffhandSlots.getOrPut(screen) {
        MovedSlot(offhandSlot, offhandSlot.x, offhandSlot.y)
    }
    setSlotPosition(
        offhandSlot,
        InventoryEquipmentLayout.HIDDEN_SLOT_X,
        InventoryEquipmentLayout.HIDDEN_SLOT_Y,
    )
}

internal fun restoreInventoryEquipmentSlotLayout(screen: AbstractContainerScreen<*>) {
    movedOffhandSlots.remove(screen)?.restore()
    restoreInventoryEquipmentSlots(screen)
}

internal fun restoreAllInventoryEquipmentSlotLayouts() {
    movedOffhandSlots.values.toList().forEach(MovedSlot::restore)
    movedOffhandSlots.clear()
    clearInventoryEquipmentSlots()
}

internal fun renderInventoryEquipmentBackground(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor) {
    updateInventoryEquipmentSlotLayout(screen)
    if (!shouldShowInventoryEquipment(screen)) return

    inventoryEquipmentSlotGeometry(screen).forEachIndexed { index, geometry ->
        drawInventoryEquipmentSlot(context, geometry.screenBounds, index)
    }
}

internal fun handleInventoryEquipmentMouseClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): InputHandlingResult {
    updateInventoryEquipmentSlotLayout(screen)
    if (!shouldShowInventoryEquipment(screen)) return InputHandlingResult.IGNORED

    val mouseX = click.x().toInt()
    val mouseY = click.y().toInt()
    if (inventoryEquipmentSlotGeometry(screen).none { it.screenBounds.contains(mouseX, mouseY) }) {
        return InputHandlingResult.IGNORED
    }
    if (isEquipmentCommandClick(click) && screen.menu.carried.isEmpty) {
        inventoryEquipmentConfig.clickAction.command?.let { command ->
            Minecraft.getInstance().connection?.sendCommand(command)
        }
    }
    return InputHandlingResult.CONSUMED
}

internal fun inventoryEquipmentSlotGeometry(left: Int, top: Int): List<InventoryEquipmentSlotGeometry> =
    List(ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT) { index ->
        val localBounds = Rect(
            InventoryEquipmentLayout.SLOT_X,
            InventoryEquipmentLayout.FIRST_SLOT_Y + InventoryEquipmentLayout.SLOT_STEP * index,
            InventoryEquipmentLayout.SLOT_SIZE,
            InventoryEquipmentLayout.SLOT_SIZE,
        )
        InventoryEquipmentSlotGeometry(
            localBounds = localBounds,
            screenBounds = Rect(
                left + localBounds.x,
                top + localBounds.y,
                localBounds.width,
                localBounds.height,
            ),
        )
    }

internal fun inventoryEquipmentSlotGeometry(screen: AbstractContainerScreen<*>): List<InventoryEquipmentSlotGeometry> {
    val accessor = screen as AbstractContainerScreenAccessor
    return inventoryEquipmentSlotGeometry(accessor.skysoftGetLeftPos(), accessor.skysoftGetTopPos())
}

internal data class InventoryEquipmentSlotGeometry(
    val localBounds: Rect,
    val screenBounds: Rect,
)

internal fun shouldShowInventoryEquipment(screen: AbstractContainerScreen<*>): Boolean =
    screen is InventoryScreen &&
        isInventoryEquipmentAvailable() &&
        !StorageOverlayController.isActive(screen)

private fun drawInventoryEquipmentSlot(context: GuiGraphicsExtractor, bounds: Rect, index: Int) {
    context.blit(
        RenderPipelines.GUI_TEXTURED,
        AbstractContainerScreen.INVENTORY_LOCATION,
        bounds.x - InventoryEquipmentLayout.SLOT_TEXTURE_OFFSET,
        bounds.y - InventoryEquipmentLayout.SLOT_TEXTURE_OFFSET,
        InventoryEquipmentLayout.ARMOR_SLOT_TEXTURE_SOURCE_X.toFloat(),
        InventoryEquipmentLayout.armorSlotTextureSourceY(index).toFloat(),
        InventoryEquipmentLayout.SLOT_TEXTURE_SIZE,
        InventoryEquipmentLayout.SLOT_TEXTURE_SIZE,
        InventoryEquipmentLayout.INVENTORY_TEXTURE_SIZE,
        InventoryEquipmentLayout.INVENTORY_TEXTURE_SIZE,
    )
}

internal fun drawInventoryEquipmentSlotRightEdge(context: GuiGraphicsExtractor, bounds: Rect, index: Int) {
    val outsideRightEdgeSourceX = InventoryEquipmentLayout.ARMOR_SLOT_TEXTURE_SOURCE_X +
        InventoryEquipmentLayout.SLOT_TEXTURE_SIZE
    val insideRightEdgeSourceX = outsideRightEdgeSourceX - 1

    drawInventoryEquipmentSlotRightEdgeColumn(
        context,
        bounds,
        index,
        outsideRightEdgeSourceX,
    )
    drawInventoryEquipmentSlotRightEdgeColumn(
        context,
        bounds,
        index,
        insideRightEdgeSourceX,
    )
}

private fun drawInventoryEquipmentSlotRightEdgeColumn(
    context: GuiGraphicsExtractor,
    bounds: Rect,
    index: Int,
    sourceX: Int,
) {
    context.blit(
        RenderPipelines.GUI_TEXTURED,
        AbstractContainerScreen.INVENTORY_LOCATION,
        bounds.x + bounds.width,
        bounds.y - InventoryEquipmentLayout.SLOT_TEXTURE_OFFSET,
        sourceX.toFloat(),
        InventoryEquipmentLayout.armorSlotTextureSourceY(index).toFloat(),
        1,
        InventoryEquipmentLayout.SLOT_TEXTURE_SIZE,
        InventoryEquipmentLayout.INVENTORY_TEXTURE_SIZE,
        InventoryEquipmentLayout.INVENTORY_TEXTURE_SIZE,
    )
}

private fun isEquipmentCommandClick(click: MouseButtonEvent): Boolean =
    click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT ||
        click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT

private fun warnMissingOffhandSlot() {
    if (warnedMissingOffhandSlot) return
    warnedMissingOffhandSlot = true
    SkysoftMod.LOGGER.warn("Inventory equipment could not move the offhand slot because slot 45 is missing.")
}

private data class MovedSlot(
    val slot: Slot,
    val x: Int,
    val y: Int,
) {
    fun restore() = setSlotPosition(slot, x, y)
}

internal object InventoryEquipmentLayout {
    const val SLOT_X = 77
    const val FIRST_SLOT_Y = 8
    const val SLOT_STEP = 18
    const val SLOT_SIZE = 16
    const val SLOT_TEXTURE_SIZE = 18
    const val SLOT_TEXTURE_OFFSET = 1
    const val SLOT_HIGHLIGHT_SIZE = 24
    const val SLOT_HIGHLIGHT_OFFSET = 4
    const val ARMOR_SLOT_TEXTURE_SOURCE_X = 7
    const val FIRST_ARMOR_SLOT_TEXTURE_SOURCE_Y = 7
    const val INVENTORY_TEXTURE_SIZE = 256
    const val OFFHAND_SLOT_INDEX = 45
    const val HIDDEN_SLOT_X = -10_000
    const val HIDDEN_SLOT_Y = -10_000

    fun armorSlotTextureSourceY(index: Int): Int = FIRST_ARMOR_SLOT_TEXTURE_SOURCE_Y + SLOT_STEP * index
}
