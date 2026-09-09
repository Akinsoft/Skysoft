package com.skysoft.features.inventory

import com.skysoft.config.SetMenuKeybindsConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

object MenuKeybinds {
    private val config get() = SkysoftConfigGui.config().inventory
    private val menuTitle = Regex("\\([1-9]\\d*/[1-9]\\d*\\) (Armor Sets|Equipment Sets|Loadouts)")
    private val setSlots = (36..44).toList()
    private val loadoutSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)

    fun handleBinding(
        screen: AbstractContainerScreen<*>,
        binding: Int,
        repeated: Boolean = false,
    ): InputHandlingResult {
        if (binding == GLFW.GLFW_KEY_UNKNOWN || (screen.focused as? EditBox)?.canConsumeInput() == true) {
            return InputHandlingResult.IGNORED
        }
        val menu = activeBindings(screen) ?: return InputHandlingResult.IGNORED
        val position = menu.keys.indexOf(binding)
        if (position < 0) return InputHandlingResult.IGNORED
        if (repeated || !screen.menu.carried.isEmpty) return InputHandlingResult.CONSUMED
        val slot = screen.menu.slots[menu.slots[position]]
        val equipAction = if (menu.isLoadout) "Left-click to equip!" else "Click to equip!"
        val canClick = slot.isActive && slot.item.loreLines().any { line ->
            val text = line.cleanSkyBlockText()
            text == equipAction || menu.allowUnequipping && text == "Click to unequip!"
        }
        if (canClick) {
            (screen as AbstractContainerScreenAccessor).skysoftSlotClicked(
                slot,
                slot.index,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                ContainerInput.PICKUP,
            )
        }
        return InputHandlingResult.CONSUMED
    }

    fun handleMouseRelease(screen: AbstractContainerScreen<*>, button: Int): InputHandlingResult =
        if (activeBindings(screen)?.keys?.contains(button) == true) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }

    fun keybindLabel(screen: AbstractContainerScreen<*>, slot: Slot): String? {
        if (!slot.hasItem() || slot.index !in setSlots && slot.index !in loadoutSlots) return null
        val menu = activeBindings(screen) ?: return null
        val position = menu.slots.indexOf(slot.index)
        if (!menu.showKeybinds || position < 0 || !slot.isActive || slot.item.hoverName.cleanSkyBlockText().isBlank()) {
            return null
        }
        return menu.keys[position].takeUnless { it == GLFW.GLFW_KEY_UNKNOWN }?.let(InputUtilities::bindingName)
    }

    fun renderKeybind(context: GuiGraphicsExtractor, font: Font, label: String, x: Int, y: Int) {
        val width = font.width(label)
        val scale = minOf(1f, SLOT_SIZE.toFloat() / width.coerceAtLeast(1))
        val pose = context.pose()
        pose.pushMatrix()
        try {
            pose.translate(x + COUNT_RIGHT, y + COUNT_TOP + font.lineHeight * (1f - scale))
            pose.scale(scale, scale)
            context.text(font, label, -width, 0, -1, true)
        } finally {
            pose.popMatrix()
        }
    }

    private fun activeBindings(screen: AbstractContainerScreen<*>): MenuBindings? {
        if (!HypixelLocationState.inSkyBlock) return null
        if (!config.wardrobeKeybinds.enabled && !config.equipmentKeybinds.enabled && !config.loadoutKeybinds.enabled) {
            return null
        }
        val chest = screen.menu as? ChestMenu ?: return null
        if (chest.container.containerSize != MENU_SIZE) return null
        val name = menuTitle.matchEntire(screen.title.cleanSkyBlockText())?.groupValues?.get(1) ?: return null
        return when (name) {
            "Armor Sets" -> config.wardrobeKeybinds.activeBindings()
            "Equipment Sets" -> config.equipmentKeybinds.activeBindings()
            "Loadouts" -> config.loadoutKeybinds.takeIf { it.enabled }?.let {
                MenuBindings(loadoutSlots, it.settings.bindings(), it.details.showKeybinds, isLoadout = true)
            }
            else -> null
        }
    }

    private fun SetMenuKeybindsConfig.activeBindings(): MenuBindings? = takeIf { enabled }?.let {
        MenuBindings(setSlots, settings.bindings(), details.showKeybinds, allowUnequipping = settings.allowUnequipping)
    }

    private data class MenuBindings(
        val slots: List<Int>,
        val keys: List<Int>,
        val showKeybinds: Boolean,
        val allowUnequipping: Boolean = false,
        val isLoadout: Boolean = false,
    )

    private const val MENU_SIZE = 54
    private const val SLOT_SIZE = 16
    private const val COUNT_RIGHT = 17f
    private const val COUNT_TOP = 9f
}
