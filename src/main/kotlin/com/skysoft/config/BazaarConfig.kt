package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

enum class BazaarTrackerSound(private val displayName: String) {
    FILLED("§bFilled"),
    PARTIAL("§ePartial Fill"),
    OUTBID_UNDERCUT("§6Outbid / Undercut"),
    ;

    override fun toString(): String = displayName
}

class SkysoftBazaarConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show the Bazaar order tracker overlay.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Bazaar tracker settings.")
    @field:Accordion
    val settings = BazaarTrackerSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Bazaar tracker visual settings.")
    @field:Accordion
    val details = BazaarTrackerDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 70, 1f, centerX = false, centerY = false).rememberDefault()

    override fun repairLoadedValues() {
        settings.maxOrders = settings.maxOrders.coerceIn(
            MIN_BAZAAR_TRACKER_ORDERS,
            MAX_BAZAAR_TRACKER_ORDERS,
        )
    }
}

class BazaarTrackerSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Max Orders", desc = "Maximum active orders shown in the overlay.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 20f, minStep = 1f)
    var maxOrders = 8

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Estimate Fills",
        desc = "Estimates how much your order has filled without opening your Bazaar Orders.\n§cThis is experimental.",
    )
    @field:ConfigEditorBoolean
    var estimateFills = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Sounds",
        desc = "Bazaar tracker alert sounds. Remove entries with the trash button to disable that sound.",
    )
    @field:ConfigEditorDraggableList
    val sounds: Property<MutableList<BazaarTrackerSound>> = Property.of(
        mutableListOf(
            BazaarTrackerSound.FILLED,
            BazaarTrackerSound.PARTIAL,
            BazaarTrackerSound.OUTBID_UNDERCUT,
        ),
    )

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Only My Orders", desc = "Hide co-op members' orders and compact the Bazaar Orders menu.")
    @field:ConfigEditorBoolean
    var onlyMyOrders = false
}

class BazaarTrackerDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Only in Menus", desc = "Only show the Bazaar tracker while a container menu is open.")
    @field:ConfigEditorBoolean
    var isOnlyInMenus = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide When Empty", desc = "Hide the Bazaar tracker when no orders are being tracked.")
    @field:ConfigEditorBoolean
    var hideWhenEmpty = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Background", desc = "Draw a dark background behind the Bazaar tracker.")
    @field:ConfigEditorBoolean
    var showBackground = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Flipping Info",
        desc = "Displays Investment and Profit information, useful for Bazaar Flipping.",
    )
    @field:ConfigEditorBoolean
    var flippingInfo = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Visual Indicators", desc = "Highlight Bazaar Orders slots by tracker status.")
    @field:ConfigEditorBoolean
    var visualIndicators = true
}

private const val MIN_BAZAAR_TRACKER_ORDERS = 1

private const val MAX_BAZAAR_TRACKER_ORDERS = 20
