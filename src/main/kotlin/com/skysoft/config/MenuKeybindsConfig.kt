package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import org.lwjgl.glfw.GLFW

class SetMenuKeybindsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Use keybinds to equip sets while this menu is open.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Bind the nine columns from left to right on each page.")
    @field:Accordion
    val settings = SetMenuKeybindsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Keybind labels on menu buttons.")
    @field:Accordion
    val details = MenuKeybindsDetailsConfig()
}

class LoadoutMenuKeybindsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Use keybinds to equip loadouts while the Loadouts menu is open.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Bind loadouts from left to right, then top to bottom, on each page.")
    @field:Accordion
    val settings = LoadoutMenuKeybindsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Keybind labels on loadout buttons.")
    @field:Accordion
    val details = MenuKeybindsDetailsConfig()
}

class MenuKeybindsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Keybinds", desc = "Show each assigned key in the bottom-right corner of its button.")
    @field:ConfigEditorBoolean
    var showKeybinds = true
}

class SetMenuKeybindsSettingsConfig : MenuPositionKeybindsConfig() {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Allow Unequipping", desc = "Pressing the key for your equipped set unequips it.")
    @field:ConfigEditorBoolean
    @field:ConfigOrder(0)
    var allowUnequipping = true
}

open class MenuPositionKeybindsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 1", desc = "Key for the first position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_1)
    @field:ConfigOrder(1)
    var slot1 = GLFW.GLFW_KEY_1

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 2", desc = "Key for the second position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_2)
    @field:ConfigOrder(2)
    var slot2 = GLFW.GLFW_KEY_2

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 3", desc = "Key for the third position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_3)
    @field:ConfigOrder(3)
    var slot3 = GLFW.GLFW_KEY_3

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 4", desc = "Key for the fourth position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_4)
    @field:ConfigOrder(4)
    var slot4 = GLFW.GLFW_KEY_4

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 5", desc = "Key for the fifth position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_5)
    @field:ConfigOrder(5)
    var slot5 = GLFW.GLFW_KEY_5

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 6", desc = "Key for the sixth position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_6)
    @field:ConfigOrder(6)
    var slot6 = GLFW.GLFW_KEY_6

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 7", desc = "Key for the seventh position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_7)
    @field:ConfigOrder(7)
    var slot7 = GLFW.GLFW_KEY_7

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 8", desc = "Key for the eighth position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_8)
    @field:ConfigOrder(8)
    var slot8 = GLFW.GLFW_KEY_8

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 9", desc = "Key for the ninth position on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_9)
    @field:ConfigOrder(9)
    var slot9 = GLFW.GLFW_KEY_9

    open fun bindings(): List<Int> = listOf(slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9)
}

class LoadoutMenuKeybindsSettingsConfig : MenuPositionKeybindsConfig() {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 10", desc = "Key for the bottom-left loadout on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    @field:ConfigOrder(10)
    var slot10 = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 11", desc = "Key for the bottom-middle loadout on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    @field:ConfigOrder(11)
    var slot11 = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot 12", desc = "Key for the bottom-right loadout on the current page.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    @field:ConfigOrder(12)
    var slot12 = GLFW.GLFW_KEY_UNKNOWN

    override fun bindings(): List<Int> = super.bindings() + listOf(slot10, slot11, slot12)
}
