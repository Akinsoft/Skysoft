package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import com.skysoft.features.screenshot.ScreenshotManager
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
    @field:Category(name = "Screenshot Manager", desc = "Browse and manage Minecraft screenshots.")
    val screenshotManager = ScreenshotManagerConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Held Item", desc = "Customize first-person held item visuals and swing duration.")
    val heldItem = HeldItemConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Selected Item Name", desc = "Customize the item name shown above the hotbar.")
    val selectedItemName = SelectedItemNameConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Action Bar", desc = "Action bar visual settings.")
    val actionBar = SkysoftActionBarConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Custom Bars", desc = "Replace SkyBlock status displays with custom bars.")
    val customBars = CustomBarsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Better TAB", desc = "Compact the Hypixel SkyBlock tab list.")
    val betterTab = BetterTabConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Day Display", desc = "Show the current Minecraft day.")
    val dayDisplay = DayDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Spotify Display", desc = "Show your current Spotify playback.")
    val spotifyDisplay = SpotifyDisplayConfig()

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
    @field:Category(name = "Vanilla UI", desc = "Choose which parts of Minecraft's interface to hide.")
    val vanillaUi = VanillaUiConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Merge Absorption Hearts",
        desc = "Show absorption within the normal heart grid instead of extending the health bar.",
    )
    @field:ConfigEditorBoolean
    var areAbsorptionHeartsMerged = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Stop Heart Bobbing", desc = "Stop regeneration and low-health heart movement.")
    @field:ConfigEditorBoolean
    var isHeartBobbingDisabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Render Titles In Front", desc = "Render Skysoft titles in front of open screens.")
    @field:ConfigEditorBoolean
    var areTitlesRenderedInFront = false

    fun repairLoadedValues() {
        heldItem.repairLoadedValues()
        selectedItemName.repairLoadedValues()
        actionBar.repairLoadedValues()
        customBars.repairLoadedValues()
    }
}

class VanillaUiConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Recipe Book", desc = "Hide the recipe book in your inventory on SkyBlock.")
    @field:ConfigEditorBoolean
    var isVanillaRecipeBookHidden = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Status Effects",
        desc = "Hide status effects beside inventories and in the top-right HUD.",
    )
    @field:ConfigEditorBoolean
    var areVanillaStatusEffectsHidden = false
}

class ScreenshotManagerConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Enable screenshot sounds, messages, and the manager key.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Screenshot Manager settings.")
    @field:Accordion
    val settings = ScreenshotManagerSettingsConfig()
}

class ScreenshotManagerSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Copy to Clipboard", desc = "Copy new screenshots to the clipboard.")
    @field:ConfigEditorBoolean
    var isClipboardCopyEnabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Manager Key", desc = "Press this key to open the Screenshot Manager.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_F4)
    var managerKey = GLFW.GLFW_KEY_F4

    @JvmField
    @field:ConfigOption(name = "Open Manager", desc = "Open the Screenshot Manager.")
    @field:ConfigEditorButton(buttonText = "Open")
    val openManager = Runnable { ScreenshotManager.open() }
}

class PositionEditorConfig {
    @JvmField
    @field:Expose
    val titlePosition = HudPosition(0, -82, centerX = true, centerY = true).rememberDefault()

    @JvmField
    @field:Expose
    val scoreboardPosition = HudPosition(centerX = true, centerY = true).rememberDefault()

    @JvmField
    @field:Expose
    var isScoreboardPositionCustomized = false

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
    @field:ConfigOption(name = "Only in Storage Overlay", desc = "Only use the Inventory GUI Scale while the Storage Overlay is open.")
    @field:ConfigEditorBoolean
    var isInventoryGuiScaleStorageOnly = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Tooltip GUI Scale", desc = "Inventory tooltip GUI scale. 0 uses Minecraft's automatic scale.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 8f, minStep = 1f)
    var tooltipGuiScale = 0
}
