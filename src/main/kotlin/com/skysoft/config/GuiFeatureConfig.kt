package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import com.skysoft.gui.SkysoftHudEditor
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class GuiFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Position Editor", desc = "Move and scale HUD elements.")
    val positionEditor = PositionEditorConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Held Item", desc = "Customize first-person held item visuals and swing duration.")
    val heldItem = HeldItemConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Action Bar", desc = "Action bar visual settings.")
    val actionBar = SkysoftActionBarConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Day Display", desc = "Show the current Minecraft day.")
    val dayDisplay = DayDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Real Time Display", desc = "Show your local time.")
    val realTimeDisplay = RealTimeDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Server Info Display", desc = "Show FPS, TPS, and ping.")
    val serverInfoDisplay = ServerInfoDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Inventory & Tooltip Scale", desc = "GUI scaling for inventory screens and tooltips.")
    val inventoryScreen = InventoryScreenConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Vanilla Status Effects",
        desc = "Hide Minecraft's status effects beside inventories and in the top-right HUD.",
    )
    @field:ConfigEditorBoolean
    var areVanillaStatusEffectsHidden = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Render Titles In Front", desc = "Render Skysoft titles in front of open screens.")
    @field:ConfigEditorBoolean
    var areTitlesRenderedInFront = false

    fun repairLoadedValues() {
        heldItem.repairLoadedValues()
        actionBar.repairLoadedValues()
    }
}

class PositionEditorConfig {
    @JvmField
    @field:Expose
    val titlePosition = HudPosition(0, -82, centerX = true, centerY = true).rememberDefault()

    @JvmField
    @field:ConfigOption(name = "Editor", desc = "Open the Position Editor.")
    @field:ConfigEditorButton(buttonText = "Open")
    val openEditor = Runnable { SkysoftHudEditor.open() }

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Keybind", desc = "Press this key to open the Position Editor.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var keybind: Int = GLFW.GLFW_KEY_UNKNOWN
}

class SkysoftActionBarConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Draw a box behind the action bar.")
    @field:ConfigEditorBoolean
    var background = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Action bar visual details.")
    @field:Accordion
    val details = SkysoftActionBarDetailsConfig()

    fun repairLoadedValues() {
        details.repairLoadedValues()
    }
}

class SkysoftActionBarDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rounded Corners", desc = "Round the background corners.")
    @field:ConfigEditorBoolean
    var roundedCorners = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background Opacity", desc = "Opacity of the action bar background.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f)
    var backgroundOpacity = DEFAULT_BACKGROUND_OPACITY

    fun repairLoadedValues() {
        backgroundOpacity = backgroundOpacity.coerceIn(MIN_BACKGROUND_OPACITY, MAX_BACKGROUND_OPACITY)
    }

    private companion object {
        const val DEFAULT_BACKGROUND_OPACITY = 63
        const val MIN_BACKGROUND_OPACITY = 0
        const val MAX_BACKGROUND_OPACITY = 100
    }
}

class InventoryScreenConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Separate Inventory GUI Scale", desc = "Use a different GUI scale for inventory screens.")
    @field:ConfigEditorBoolean
    var separateInventoryGuiScale = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Separate Tooltip GUI Scale", desc = "Use a different GUI scale for inventory tooltips.")
    @field:ConfigEditorBoolean
    var separateTooltipGuiScale = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Inventory and tooltip scale settings.")
    @field:Accordion
    val settings = InventoryScreenSettingsConfig()
}

class InventoryScreenSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Inventory GUI Scale", desc = "Inventory GUI scale. 0 uses Minecraft's automatic scale.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 8f, minStep = 1f)
    var inventoryGuiScale = 0

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Tooltip GUI Scale", desc = "Inventory tooltip GUI scale. 0 uses Minecraft's automatic scale.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 8f, minStep = 1f)
    var tooltipGuiScale = 0
}
