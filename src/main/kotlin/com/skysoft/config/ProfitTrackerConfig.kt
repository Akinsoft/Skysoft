package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property

class ProfitTrackerConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Track profit from supported activities.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Profit Tracker settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = ProfitTrackerSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Profit Tracker appearance.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = ProfitTrackerDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 150, centerY = false).rememberDefault()
}

class ProfitTrackerSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Pause After", desc = "Pause time tracking after this many seconds without tracked activity.")
    @field:ConfigEditorSlider(minValue = 15f, maxValue = 900f, minStep = 15f)
    var pauseAfterSeconds = 60

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Maximum Items", desc = "Maximum tracked item rows shown at once.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 15f, minStep = 1f)
    var maximumItems = 8
}

class ProfitTrackerDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Item Icons", desc = "Show item icons beside tracked drops.")
    @field:ConfigEditorBoolean
    var showItemIcons = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Quantity Position", desc = "Choose where item quantities are shown.")
    @field:ConfigEditorDropdown
    var quantityPosition = ProfitTrackerQuantityPosition.RIGHT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Summary Lines", desc = "Choose and reorder the summary lines shown by the tracker.")
    @field:ConfigEditorDraggableList
    val summaryLines: Property<MutableList<ProfitTrackerSummaryLine>> = Property.of(
        mutableListOf(
            ProfitTrackerSummaryLine.COINS,
            ProfitTrackerSummaryLine.QUEST_COSTS,
            ProfitTrackerSummaryLine.TOTAL_PROFIT,
            ProfitTrackerSummaryLine.PROFIT_PER_HOUR,
            ProfitTrackerSummaryLine.ACTIONS,
            ProfitTrackerSummaryLine.UPTIME,
        ),
    )

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Background", desc = "Draw a dark background behind the Profit Tracker.")
    @field:ConfigEditorBoolean
    var showBackground = false
}

enum class ProfitTrackerQuantityPosition(private val displayName: String) {
    LEFT("Left"),
    RIGHT("Right"),
    ;

    override fun toString(): String = displayName
}

enum class ProfitTrackerSummaryLine(private val displayName: String) {
    @SerializedName(value = "COINS", alternate = ["MOB_KILL_COINS"])
    COINS("Coins"),
    QUEST_COSTS("Costs"),
    TOTAL_PROFIT("Total Profit"),
    PROFIT_PER_HOUR("Profit/h"),
    @SerializedName(value = "ACTIONS", alternate = ["BOSSES_KILLED"])
    ACTIONS("Actions"),
    UPTIME("Uptime"),
    ;

    override fun toString(): String = displayName
}
