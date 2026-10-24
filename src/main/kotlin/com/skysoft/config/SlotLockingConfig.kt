package com.skysoft.config

import com.google.gson.annotations.Expose
import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.features.inventory.SlotLockManager
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SlotLockingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Protect locked inventory slots.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Slot locking controls.")
    @field:Accordion
    val settings = SlotLockingSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Slot lock visual settings.")
    @field:Accordion
    val details = SlotLockingDetailsConfig()

    val lockKey: Int
        get() = settings.lockKey
}

class SlotLockingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lock Key", desc = "Press this key while hovering an inventory slot to lock or unlock it.")
    @field:ConfigEditorKeybind(defaultKey = InputConstants.KEY_L)
    var lockKey = InputConstants.KEY_L

    @JvmField
    @field:ConfigOption(name = "Reset All Locks", desc = "Unlock every inventory slot on the current SkyBlock profile.")
    @field:ConfigEditorButton(buttonText = "Reset")
    val resetAllLocks = Runnable { SlotLockManager.resetAllLocks() }
}

class SlotLockingDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lock Icon Opacity", desc = "Opacity of the lock icon drawn over locked slots.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
    var lockIconOpacity = 50
}
