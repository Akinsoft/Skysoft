package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable

class StorageOverlayConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    val settings = StorageOverlaySettingsConfig()

    @JvmField
    @field:Expose
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
        details.scrollSpeed = details.scrollSpeed.coerceIn(
            StorageOverlayConfigBounds.MIN_SCROLL_SPEED,
            StorageOverlayConfigBounds.MAX_SCROLL_SPEED,
        )
    }
}

class StorageOverlaySettingsConfig {
    @JvmField
    @field:Expose
    var mode = StorageOverlayMode.MODERN

    @JvmField
    @field:Expose
    var theme = StorageOverlayTheme.DARK

    @JvmField
    @field:Expose
    var autoOpenPrevious = true

    @JvmField
    @field:Expose
    var miniMenu = true
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
    var dimBackground = true

    @JvmField
    @field:Expose
    var columns = 3

    @JvmField
    @field:Expose
    var height = 234

    @JvmField
    @field:Expose
    var pageSpacing = StorageOverlayConfigBounds.DEFAULT_PAGE_SPACING

    @JvmField
    @field:Expose
    var scrollSpeed = 18
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
