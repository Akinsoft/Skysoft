package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.features.inventory.SlotLockManager
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class SlotLockingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Protect locked inventory slots.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Slot locking controls.")
    @field:Accordion
    val settings = SlotLockingSettingsConfig()

    val lockKey: Int
        get() = settings.lockKey
}

class SlotLockingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lock Key", desc = "Press this key while hovering an inventory slot to lock or unlock it.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_L)
    var lockKey = GLFW.GLFW_KEY_L

    @JvmField
    @field:ConfigOption(name = "Reset All Locks", desc = "Unlock every inventory slot on the current SkyBlock profile.")
    @field:ConfigEditorButton(buttonText = "Reset")
    val resetAllLocks = Runnable { SlotLockManager.resetAllLocks() }
}
