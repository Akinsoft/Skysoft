package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class StorageOverlayConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Replace SkyBlock storage screens with a searchable overlay.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Storage Overlay controls and navigation.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = StorageOverlaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Storage Overlay appearance.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = StorageOverlayDetailsConfig()

    override fun repairLoadedValues() {
        details.columns = details.columns.coerceIn(
            StorageOverlayConfigBounds.MIN_COLUMNS,
            StorageOverlayConfigBounds.MAX_COLUMNS,
        )
        details.height = details.height.coerceIn(
            StorageOverlayConfigBounds.MIN_HEIGHT,
            StorageOverlayConfigBounds.MAX_HEIGHT,
        )
        details.pageSpacing = details.pageSpacing.coerceIn(
            StorageOverlayConfigBounds.MIN_PAGE_SPACING,
            StorageOverlayConfigBounds.MAX_PAGE_SPACING,
        )
        settings.scrollSpeed = settings.scrollSpeed.coerceIn(
            StorageOverlayConfigBounds.MIN_SCROLL_SPEED,
            StorageOverlayConfigBounds.MAX_SCROLL_SPEED,
        )
    }
}

class StorageOverlaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mode", desc = "Choose the Modern page view or Classic storage panel.")
    @field:ConfigEditorDropdown
    var mode = StorageOverlayMode.MODERN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reopen Previous", desc = "Open the last viewed storage page when opening storage.")
    @field:ConfigEditorBoolean
    var autoOpenPrevious = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Shortcut", desc = "Show page shortcuts beside your inventory in Classic mode.")
    @field:ConfigEditorBoolean
    var miniMenu = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Scroll Speed", desc = "Distance scrolled per mouse wheel step.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var scrollSpeed = 18
}

enum class StorageOverlayMode(private val displayName: String) {
    MODERN("Modern"),
    CLASSIC("Classic"),
    ;

    override fun toString(): String = displayName
}

enum class StorageOverlayTheme(private val displayName: String) {
    DARK("Dark"),
    LIGHT("Light"),
    ;

    override fun toString(): String = displayName
}

class StorageOverlayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Theme", desc = "Choose a dark or light storage overlay.")
    @field:ConfigEditorDropdown
    var theme = StorageOverlayTheme.DARK

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Dim Background", desc = "Darken the world behind the storage overlay.")
    @field:ConfigEditorBoolean
    var dimBackground = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Columns", desc = "Maximum number of storage pages per row.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 9f, minStep = 1f)
    var columns = 3

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Height", desc = "Height of the storage panel in Classic mode.")
    @field:ConfigEditorSlider(minValue = 96f, maxValue = 720f, minStep = 18f)
    var height = 234

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Page Spacing", desc = "Space between storage pages.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 16f, minStep = 1f)
    var pageSpacing = StorageOverlayConfigBounds.DEFAULT_PAGE_SPACING
}

internal object StorageOverlayConfigBounds {
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 9
    const val MIN_HEIGHT = 96
    const val MAX_HEIGHT = 720
    const val HEIGHT_STEP = 18
    const val MIN_PAGE_SPACING = 0
    const val MAX_PAGE_SPACING = 16
    const val DEFAULT_PAGE_SPACING = 8
    const val MIN_SCROLL_SPEED = 1
    const val MAX_SCROLL_SPEED = 40
}
