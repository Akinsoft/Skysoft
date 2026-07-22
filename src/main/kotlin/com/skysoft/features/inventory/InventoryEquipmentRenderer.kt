package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.gui.Rect
import java.util.IdentityHashMap
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

private val equipmentSlotLayouts = IdentityHashMap<AbstractContainerScreen<*>, InventoryEquipmentSlotLayout>()
private val slotHighlightBackSprite = Identifier.withDefaultNamespace("container/slot_highlight_back")
private val slotHighlightFrontSprite = Identifier.withDefaultNamespace("container/slot_highlight_front")

internal fun renderInventoryEquipment(
    screen: AbstractContainerScreen<*>,
    context: GuiGraphicsExtractor,
    mouseX: Int,
    mouseY: Int,
) {
    updateInventoryEquipmentSlotLayout(screen)
    if (!shouldShowInventoryEquipment(screen)) return

    val slots = equipmentSlotLayouts.getOrPut(screen, ::InventoryEquipmentSlotLayout)
    slots.setItems(InventoryEquipmentCache.stacks())
    for ((index, geometry) in inventoryEquipmentSlotGeometry(screen).withIndex()) {
        val hovered = geometry.screenBounds.contains(mouseX, mouseY)
        val renderBounds = geometry.localBounds
        if (hovered) drawEquipmentHighlight(context, renderBounds, front = false)
        val slot = slots[index]
        RarityHighlightRenderer.renderBackground(context, slot.item, renderBounds.x, renderBounds.y)
        (screen as AbstractContainerScreenAccessor).skysoftExtractSlot(context, slot, mouseX, mouseY)
        drawInventoryEquipmentSlotRightEdge(context, renderBounds, index)
        if (hovered) {
            drawEquipmentHighlight(context, renderBounds, front = true)
            val stack = slot.item
            if (!stack.isEmpty) {
                context.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY)
            } else {
                inventoryEquipmentClickTooltip()?.let { tooltip ->
                    context.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, mouseX, mouseY)
                }
            }
        }
    }
}

private fun inventoryEquipmentClickTooltip(): Component? =
    inventoryEquipmentConfig.clickAction.command
        ?.let { command -> Component.literal("Open /$command").withStyle(ChatFormatting.GRAY) }

internal fun restoreInventoryEquipmentSlots(screen: AbstractContainerScreen<*>) {
    equipmentSlotLayouts.remove(screen)
}

internal fun clearInventoryEquipmentSlots() = equipmentSlotLayouts.clear()

internal fun isInventoryEquipmentSlot(slot: Slot?): Boolean = slot is InventoryEquipmentSlot

private fun drawEquipmentHighlight(context: GuiGraphicsExtractor, bounds: Rect, front: Boolean) {
    val sprite = if (front) {
        slotHighlightFrontSprite
    } else {
        slotHighlightBackSprite
    }
    context.blitSprite(
        RenderPipelines.GUI_TEXTURED,
        sprite,
        bounds.x - InventoryEquipmentLayout.SLOT_HIGHLIGHT_OFFSET,
        bounds.y - InventoryEquipmentLayout.SLOT_HIGHLIGHT_OFFSET,
        InventoryEquipmentLayout.SLOT_HIGHLIGHT_SIZE,
        InventoryEquipmentLayout.SLOT_HIGHLIGHT_SIZE,
    )
}

private class InventoryEquipmentSlotLayout {
    private val container = SimpleContainer(ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT)
    private val slots = List(ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT) { index ->
        InventoryEquipmentSlot(
            container,
            index,
            InventoryEquipmentLayout.SLOT_X,
            InventoryEquipmentLayout.FIRST_SLOT_Y + InventoryEquipmentLayout.SLOT_STEP * index,
        )
    }

    operator fun get(index: Int): Slot = slots[index]

    fun setItems(stacks: List<ItemStack>) {
        slots.indices.forEach { index ->
            container.setItem(index, stacks.getOrNull(index)?.copy() ?: ItemStack.EMPTY)
        }
    }
}

private class InventoryEquipmentSlot(
    container: SimpleContainer,
    index: Int,
    x: Int,
    y: Int,
) : Slot(container, index, x, y) {
    override fun mayPickup(player: Player): Boolean = false

    override fun mayPlace(stack: ItemStack): Boolean = false
}
