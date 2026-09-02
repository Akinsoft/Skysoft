package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class ItemListConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show Skysoft's Item List on Hypixel inventory screens.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Item List controls and data.")
    @field:Accordion
    val settings = ItemListSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Item List display options.")
    @field:Accordion
    val sources = ItemListSourcesConfig()

    @JvmField
    @field:Expose
    var favorites: MutableList<String> = mutableListOf()

    override fun repairLoadedValues() {
        favorites = favorites.filter(String::isNotBlank).distinct().take(MAX_FAVORITES).toMutableList()
        sources.searchPosition.rememberDefault(ItemListSourcesConfig.defaultSearchPosition())
        settings.repairLoadedValues()
        sources.repairLoadedValues()
    }

    companion object {
        const val MAX_FAVORITES = 64
    }
}

class ItemListSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Item Scale", desc = "Size of items inside the Item List.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 3f, minStep = ITEM_SCALE_STEP)
    var itemScale = DEFAULT_ITEM_SCALE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "List Width", desc = "Width in standard item slots.")
    @field:ConfigEditorSlider(minValue = 2f, maxValue = 32f, minStep = 1f)
    var columns = DEFAULT_COLUMNS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "List Height", desc = "Height in standard item rows. 0 fills the available height.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 32f, minStep = 1f)
    var rows = DEFAULT_ROWS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Vanilla Items", desc = "Include Minecraft items.")
    @field:ConfigEditorBoolean
    var showVanilla = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Visibility Key", desc = "Temporarily show or hide Item List.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_KP_SUBTRACT)
    var visibilityKey = GLFW.GLFW_KEY_KP_SUBTRACT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Tab Search", desc = "Focus the Item List search bar by pressing Tab.")
    @field:ConfigEditorBoolean
    var isTabSearchEnabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Right-Click Clear", desc = "Clear Item List search by right-clicking the search bar.")
    @field:ConfigEditorBoolean
    var isRightClickClearEnabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Info Key", desc = "Open the hovered Item List entry's Info tab.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_I)
    var infoKey = GLFW.GLFW_KEY_I

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Recipes Key", desc = "Open the hovered Item List entry's Obtain or Drops tab.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_R)
    var recipesKey = GLFW.GLFW_KEY_R

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Uses Key", desc = "Open the hovered Item List entry's Uses tab.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_U)
    var usesKey = GLFW.GLFW_KEY_U

    @JvmField
    @field:ConfigOption(name = "Refresh Data", desc = "Check for updated Item List data.")
    @field:ConfigEditorButton(buttonText = "Refresh")
    val refreshData = Runnable { com.skysoft.data.skyblock.SkyBlockDataRepository.reload() }

    @JvmField
    @field:ConfigOption(name = "Clear Favorites", desc = "Clear all favorite items.")
    @field:ConfigEditorButton(buttonText = "Clear")
    val clearFavorites = Runnable {
        SkysoftConfigGui.config().inventory.itemList.favorites.clear()
        SkysoftConfigGui.config().saveNow()
    }

    fun repairLoadedValues() {
        itemScale = itemScale.takeIf { it.isFinite() }?.coerceIn(MIN_ITEM_SCALE, MAX_ITEM_SCALE) ?: DEFAULT_ITEM_SCALE
        columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        rows = rows.coerceIn(MIN_ROWS, MAX_ROWS)
    }

    companion object {
        const val DEFAULT_ITEM_SCALE = 1f
        const val MIN_ITEM_SCALE = 1f
        const val MAX_ITEM_SCALE = 3f
        const val ITEM_SCALE_STEP = 0.1f
        const val DEFAULT_COLUMNS = 9
        const val DEFAULT_ROWS = 0
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 32
        const val MIN_ROWS = 0
        const val MAX_ROWS = 32
    }
}

class ItemListSourcesConfig {
    @JvmField
    @field:Expose
    val searchPosition = defaultSearchPosition().rememberDefault()

    @JvmField
    @field:Expose
    var searchWidth = DEFAULT_SEARCH_WIDTH

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Roman Numerals", desc = "Show enchantment tiers with Roman numerals.")
    @field:ConfigEditorBoolean
    var useRomanNumerals = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Item Backgrounds", desc = "Draw slot backgrounds behind Item List items.")
    @field:ConfigEditorBoolean
    var showItemBackgrounds = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Settings Button", desc = "Expand the search bar across the settings button space.")
    @field:ConfigEditorBoolean
    var isSettingsButtonHidden = false

    @JvmField
    @field:Expose
    var bazaarGraphMode = "PRICE_HISTORY"

    @JvmField
    @field:Expose
    var bazaarGraphWindow = "ONE_HOUR"

    @JvmField
    @field:Expose
    var showBazaarBuyData = true

    @JvmField
    @field:Expose
    var showBazaarSellData = true

    @JvmField
    @field:Expose
    @field:SerializedName(value = "showBazaarPlayerData", alternate = ["showBazaarOrders"])
    var showBazaarPlayerData = true

    fun repairLoadedValues() {
        searchPosition.scale = HudPosition.DEFAULT_SCALE
        searchWidth = searchWidth.coerceIn(MIN_SEARCH_WIDTH, MAX_SEARCH_WIDTH)
        bazaarGraphMode = when (bazaarGraphMode) {
            "PRICE" -> "ORDER_BOOK"
            "ACTIVITY" -> "TRADE_VOLUME"
            in BAZAAR_GRAPH_MODES -> bazaarGraphMode
            else -> "PRICE_HISTORY"
        }
        if (bazaarGraphWindow !in BAZAAR_GRAPH_WINDOWS) bazaarGraphWindow = "ONE_HOUR"
    }

    companion object {
        const val DEFAULT_SEARCH_WIDTH = 162
        const val MIN_SEARCH_WIDTH = 72
        const val SEARCH_WIDTH_STEP = 18
        const val MAX_SEARCH_WIDTH = ItemListSettingsConfig.MAX_COLUMNS * SEARCH_WIDTH_STEP * 3

        fun defaultSearchPosition() = HudPosition(
            DEFAULT_SEARCH_OFFSET,
            DEFAULT_SEARCH_OFFSET,
            centerY = false,
        )

        private const val DEFAULT_SEARCH_OFFSET = -4
        private val BAZAAR_GRAPH_MODES = setOf("PRICE_HISTORY", "ORDER_BOOK", "TRADE_VOLUME")
        private val BAZAAR_GRAPH_WINDOWS = setOf(
            "FIFTEEN_MINUTES",
            "THIRTY_MINUTES",
            "ONE_HOUR",
            "SIX_HOURS",
            "TWENTY_FOUR_HOURS",
            "SEVEN_DAYS",
            "THIRTY_DAYS",
        )
    }
}
