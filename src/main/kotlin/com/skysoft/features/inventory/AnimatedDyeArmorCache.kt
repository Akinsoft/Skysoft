package com.skysoft.features.inventory

import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.utils.SkysoftClientEvents
import java.util.EnumMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object AnimatedDyeArmorCache {
    private val stacks = EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot::class.java)

    fun register() {
        SkysoftClientEvents.onDisconnect("Animated dye armor cache reset", stacks::clear)
    }

    @JvmStatic
    fun observe(inventory: Inventory, slot: Int, stack: ItemStack) {
        if (inventory.player !== Minecraft.getInstance().player) return
        val equipmentSlot = Inventory.EQUIPMENT_SLOT_MAPPING[slot]
            ?.takeIf { it.type == EquipmentSlot.Type.HUMANOID_ARMOR }
            ?: return
        remember(equipmentSlot, stack)
    }

    @JvmStatic
    fun observeWardrobeSelection(
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
        button: Int,
        action: ContainerInput,
    ) {
        if (slot == null || button != 0 || action != ContainerInput.PICKUP) return
        val indexes = wardrobeArmorSlotIndexes(screen.title.string, slot.index, slot.item.hoverName.string) ?: return
        val selectedStacks = indexes.map { screen.menu.slots.getOrNull(it)?.item ?: return }
        armorSlots.zip(selectedStacks).forEach { (equipmentSlot, stack) -> remember(equipmentSlot, stack) }
    }

    @JvmStatic
    fun displayStack(screen: AbstractContainerScreen<*>, slot: Slot?, stack: ItemStack): ItemStack {
        val inventoryMenu = Minecraft.getInstance().player?.inventoryMenu ?: return stack
        if (screen.menu !== inventoryMenu) return stack
        val equipmentSlot = slot?.let { menuArmorSlot(it.index) } ?: return stack
        return displayStack(stack, stacks[equipmentSlot])
    }

    internal fun displayStack(current: ItemStack, remembered: ItemStack?): ItemStack {
        if (!current.hasHiddenTooltip() || remembered == null || remembered.hasHiddenTooltip()) return current
        if (current.item != remembered.item || current.count != remembered.count) return current
        val itemId = current.skyBlockId() ?: return current
        return remembered.takeIf { it.skyBlockId() == itemId } ?: current
    }

    private fun remember(slot: EquipmentSlot, stack: ItemStack) {
        when {
            stack.isEmpty -> stacks.remove(slot)
            !stack.hasHiddenTooltip() -> stacks[slot] = stack.copy()
        }
    }

    private fun ItemStack.hasHiddenTooltip(): Boolean =
        get(DataComponents.TOOLTIP_DISPLAY)?.hideTooltip == true

    private fun menuArmorSlot(slot: Int): EquipmentSlot? =
        armorSlots.getOrNull(slot - InventoryMenu.ARMOR_SLOT_START)

    private val armorSlots = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )
}

internal fun wardrobeArmorSlotIndexes(title: String, selectorIndex: Int, selectorName: String): List<Int>? {
    if (!WARDROBE_TITLE.matches(title) || selectorIndex !in WARDROBE_SELECTORS || !WARDROBE_READY.matches(selectorName)) {
        return null
    }
    val column = selectorIndex - WARDROBE_SELECTORS.first
    return List(WARDROBE_ARMOR_PIECES) { row -> row * WARDROBE_COLUMNS + column }
}

private const val WARDROBE_ARMOR_PIECES = 4
private const val WARDROBE_COLUMNS = 9
private val WARDROBE_SELECTORS = 36..44
private val WARDROBE_TITLE = Regex("""^\(\d+/\d+\) Armor Sets$""")
private val WARDROBE_READY = Regex("""^Slot \d+: Ready$""")
