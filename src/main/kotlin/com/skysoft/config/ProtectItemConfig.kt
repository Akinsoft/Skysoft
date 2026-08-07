package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.features.inventory.ItemProtectionManager
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property
import org.lwjgl.glfw.GLFW

class ProtectItemConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Prevent protected items from being dropped or sold.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Item protection controls.")
    @field:Accordion
    val settings = ProtectItemSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Protected item visual details.")
    @field:Accordion
    val details = ProtectItemDetailsConfig()
}

class ProtectItemSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Protect Key", desc = "Press this key while hovering an inventory item to protect or unprotect it.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var protectKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Allow Dungeon Ultimates",
        desc = "Let the drop key activate ultimates while holding protected items in Dungeons.",
    )
    @field:ConfigEditorBoolean
    var allowDungeonUltimates = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Drop Messages", desc = "Hide chat messages when a protected item drop is blocked.")
    @field:ConfigEditorBoolean
    var hideProtectedItemDropMessages = false

    @JvmField
    @field:ConfigOption(name = "Reset Protected Items", desc = "Unprotect every item on the current SkyBlock profile.")
    @field:ConfigEditorButton(buttonText = "Reset")
    val resetProtectedItems = Runnable { ItemProtectionManager.resetProtectedItems() }
}

class ProtectItemDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Protected Item Star", desc = "Show a small star on protected items.")
    @field:ConfigEditorBoolean
    var showProtectedItemStar = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Star Color", desc = "Color used for protected item stars.")
    @field:ConfigEditorColour
    @field:ConfigVisibleIf("showProtectedItemStar")
    val protectedItemStarColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(255, 213, 79, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Star Size", desc = "Size of protected item stars.")
    @field:ConfigEditorSlider(minValue = 0.5f, maxValue = 1.5f, minStep = 0.1f)
    @field:ConfigVisibleIf("showProtectedItemStar")
    var protectedItemStarScale = 1f

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Star Opacity", desc = "Opacity of protected item stars.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
    @field:ConfigVisibleIf("showProtectedItemStar")
    var protectedItemStarOpacity = 100
}

enum class SlotBindingHighlightStyle(private val displayName: String) {
    FILL("Fill"),
    EDGES("Edges"),
    ;

    override fun toString(): String = displayName
}
