package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.observer.Property

enum class ServerInfoMetric(
    private val displayName: String,
    internal val symbol: String,
) {
    FPS("FPS", "⚡"),
    TPS("TPS", "⇄"),
    PING("Ping", "📶"),
    ;

    override fun toString(): String = displayName
}

enum class ServerInfoDisplayStyle(private val displayName: String) {
    SIMPLE("Simple"),
    SPLIT("Split"),
    ;

    override fun toString(): String = displayName
}

enum class ServerInfoLayout(private val displayName: String) {
    VERTICAL("Vertical"),
    HORIZONTAL("Horizontal"),
    ;

    override fun toString(): String = displayName
}

enum class DisplayLabelStyle(private val displayName: String) {
    TEXT("Text"),
    SYMBOLS("Symbols"),
    VALUES_ONLY("Values Only"),
    ;

    internal fun prefix(label: String, symbol: String): String = when (this) {
        TEXT -> "$label: "
        SYMBOLS -> "$symbol "
        VALUES_ONLY -> ""
    }

    override fun toString(): String = displayName
}

class ServerInfoDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show server performance information.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose and order displayed information.")
    @field:Accordion
    val settings = ServerInfoDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the display appearance.")
    @field:Accordion
    val details = ServerInfoDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 30, centerY = false).rememberDefault()

    @JvmField
    @field:Expose
    val splitFpsPosition = HudPosition(8, 30, centerY = false).rememberDefault()

    @JvmField
    @field:Expose
    val splitTpsPosition = HudPosition(8, 43, centerY = false).rememberDefault()

    @JvmField
    @field:Expose
    val splitPingPosition = HudPosition(8, 56, centerY = false).rememberDefault()

    internal fun splitPosition(metric: ServerInfoMetric): HudPosition = when (metric) {
        ServerInfoMetric.FPS -> splitFpsPosition
        ServerInfoMetric.TPS -> splitTpsPosition
        ServerInfoMetric.PING -> splitPingPosition
    }
}

class ServerInfoDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Displayed Info", desc = "Server information to show, in display order.")
    @field:ConfigEditorDraggableList
    val metrics: Property<MutableList<ServerInfoMetric>> = Property.of(
        mutableListOf(ServerInfoMetric.FPS, ServerInfoMetric.TPS, ServerInfoMetric.PING),
    )
}

class ServerInfoDisplayDetailsConfig {
    val simpleLayoutVisible: Property<Boolean> = Property.wrap(object : GetSetter<Boolean> {
        override fun get(): Boolean = style == ServerInfoDisplayStyle.SIMPLE

        override fun set(value: Boolean) = Unit
    })

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Style", desc = "Show one combined display or a separate display for each value.")
    @field:ConfigEditorDropdown
    var style = ServerInfoDisplayStyle.SIMPLE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Simple Layout", desc = "Arrange the Simple display vertically or horizontally.")
    @field:ConfigVisibleIf("simpleLayoutVisible")
    @field:ConfigEditorDropdown
    var layout = ServerInfoLayout.VERTICAL

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Labels", desc = "Show text labels, symbols, or values only.")
    @field:ConfigEditorDropdown
    var labelStyle = DisplayLabelStyle.TEXT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw a background behind the server information.")
    @field:ConfigEditorBoolean
    var background = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "FPS Color", desc = "Color used for FPS text and symbols.")
    @field:ConfigEditorColour
    val fpsColor: Property<ChromaColour> = defaultTextColor()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "TPS Color", desc = "Color used for TPS text and symbols.")
    @field:ConfigEditorColour
    val tpsColor: Property<ChromaColour> = defaultTextColor()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ping Color", desc = "Color used for ping text and symbols.")
    @field:ConfigEditorColour
    val pingColor: Property<ChromaColour> = defaultTextColor()

    internal fun color(metric: ServerInfoMetric): Property<ChromaColour> = when (metric) {
        ServerInfoMetric.FPS -> fpsColor
        ServerInfoMetric.TPS -> tpsColor
        ServerInfoMetric.PING -> pingColor
    }
}

private fun defaultTextColor(): Property<ChromaColour> = Property.of(
    ChromaColour.fromRGB(WHITE_COLOR_CHANNEL, WHITE_COLOR_CHANNEL, WHITE_COLOR_CHANNEL, 0, WHITE_COLOR_CHANNEL),
)

private const val WHITE_COLOR_CHANNEL = 255
