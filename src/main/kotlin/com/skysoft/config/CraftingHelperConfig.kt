package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class CraftingHelperConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show the materials needed for selected crafting targets.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Crafting Helper settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = CraftingHelperSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Crafting Helper appearance.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = CraftingHelperDetailsConfig()

    @JvmField
    @field:Expose
    val targets: MutableMap<String, Long> = mutableMapOf()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 210, centerX = false, centerY = false).rememberDefault()

    override fun repairLoadedValues() {
        settings.maximumLines = settings.maximumLines.coerceIn(MINIMUM_LINES, MAXIMUM_LINES)
        val repaired = targets.entries
            .filter { (itemId, amount) -> itemId.isNotBlank() && amount > 0L }
            .associate { (itemId, amount) -> itemId.trim() to amount.coerceAtMost(MAXIMUM_TARGET_AMOUNT) }
        targets.clear()
        targets.putAll(repaired)
    }
}

class CraftingHelperSettingsConfig {
    @JvmField
    @field:ConfigOption(
        name = "Crafting Targets",
        desc = "Open any inventory, then use ... on the HUD to add items. Click a target to manage its amount.",
    )
    @field:ConfigEditorInfoText
    val targetsHelp: Unit = Unit

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Maximum Lines", desc = "Maximum recipe rows shown at once.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 32f, minStep = 1f)
    var maximumLines = 16

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide When Empty", desc = "Hide the Crafting Helper when no crafting targets are set.")
    @field:ConfigEditorBoolean
    var hideWhenEmpty = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Copy Amount", desc = "Copy missing item amounts and Supercraft counts when you left-click entries.")
    @field:ConfigEditorBoolean
    var copyAmount = true

    @JvmField
    @field:ConfigOption(name = "Clear Targets", desc = "Remove every Crafting Helper target.")
    @field:ConfigEditorButton(buttonText = "Clear")
    val clearTargets = Runnable {
        val config = SkysoftConfigGui.config().inventory.craftingHelper
        if (config.targets.isEmpty()) return@Runnable
        config.targets.clear()
        SkysoftConfigGui.config().saveNow()
    }
}

class CraftingHelperDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Title", desc = "Show the Crafting Helper title above the recipe tree.")
    @field:ConfigEditorBoolean
    var showTitle = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Item Icons", desc = "Show item icons beside recipe entries.")
    @field:ConfigEditorBoolean
    var showItemIcons = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Background", desc = "Draw a dark background behind the Crafting Helper.")
    @field:ConfigEditorBoolean
    var showBackground = false
}

internal const val CRAFTING_HELPER_MAXIMUM_LINES = 32
internal const val CRAFTING_HELPER_MAXIMUM_TARGET_AMOUNT = 1_000_000L
private const val MINIMUM_LINES = 1
private const val MAXIMUM_LINES = CRAFTING_HELPER_MAXIMUM_LINES
private const val MAXIMUM_TARGET_AMOUNT = CRAFTING_HELPER_MAXIMUM_TARGET_AMOUNT
