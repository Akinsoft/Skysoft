package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.HudPosition
import com.skysoft.config.core.coerceFiniteIn
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.itemlist.ItemListOrder
import com.skysoft.features.inventory.itemlist.ItemListTypeFilter
import com.skysoft.utils.MinecraftInput
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

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
    @field:ConfigOption(name = "Sort & Filter", desc = "Choose which sort and filter buttons are shown.")
    @field:Accordion
    val sortBar = ItemListSortBarConfig()

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
        repairLoadedConfigs(settings, sources)
    }

    companion object {
        const val MAX_FAVORITES = 64
    }
}

class ItemListSettingsConfig : ConfigRepairable {
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
    var listFilter = ItemListTypeFilter.ALL.name

    @JvmField
    @field:Expose
    var listOrder = ItemListOrder.RELEVANCE.name

    @JvmField
    @field:Expose
    var sortNameDescending = false

    @JvmField
    @field:Expose
    var sortRarityDescending = true

    @JvmField
    @field:Expose
    var sortPriceDescending = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Visibility Key", desc = "Temporarily show or hide Item List.")
    @field:ConfigEditorKeybind(defaultKey = MinecraftInput.KEYPAD_SUBTRACT_KEY)
    var visibilityKey = MinecraftInput.KEYPAD_SUBTRACT_KEY

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
    @field:ConfigEditorKeybind(defaultKey = InputConstants.KEY_I)
    var infoKey = InputConstants.KEY_I

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Recipes Key", desc = "Open the hovered Item List entry's Obtain or Drops tab.")
    @field:ConfigEditorKeybind(defaultKey = InputConstants.KEY_R)
    var recipesKey = InputConstants.KEY_R

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Uses Key", desc = "Open the hovered Item List entry's Uses tab.")
    @field:ConfigEditorKeybind(defaultKey = InputConstants.KEY_U)
    var usesKey = InputConstants.KEY_U

    @JvmField
    @field:ConfigOption(name = "Refresh Data", desc = "Check for updated Item List data.")
    @field:ConfigEditorButton(buttonText = "Refresh")
    val refreshData = Runnable { SkyBlockDataRepository.reload() }

    @JvmField
    @field:ConfigOption(name = "Clear Favorites", desc = "Clear all favorite items.")
    @field:ConfigEditorButton(buttonText = "Clear")
    val clearFavorites = Runnable {
        SkysoftConfigGui.config().items.itemList.favorites.clear()
        SkysoftConfigGui.config().saveNow()
    }

    override fun repairLoadedValues() {
        itemScale = itemScale.coerceFiniteIn(MIN_ITEM_SCALE, MAX_ITEM_SCALE, DEFAULT_ITEM_SCALE)
        columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        rows = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        listFilter = ItemListTypeFilter.fromStoredName(listFilter).name
        listOrder = ItemListOrder.fromStoredName(listOrder).name
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

class ItemListSortBarConfig {
    @JvmField
    @field:ConfigOption(
        name = "Buttons",
        desc = "Click a button to sort or filter the list. Click that same button again to turn it off. " +
            "Right-click a sort button to reverse its direction.",
    )
    @field:ConfigEditorInfoText
    val buttonHelp: Unit = Unit

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Show Buttons",
        desc = "Show the sort and filter buttons. Turn this off to hide the whole row.",
    )
    @field:ConfigEditorBoolean
    var enabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Sort", desc = "Sort buttons, shown on the left of the row.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val sort = ItemListSortButtonsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Filter", desc = "Filter buttons, shown on the right of the row.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val filters = ItemListFilterButtonsConfig()

    internal fun shows(order: ItemListOrder): Boolean = when (order) {
        ItemListOrder.NAME -> sort.name
        ItemListOrder.RARITY -> sort.rarity
        ItemListOrder.PRICE -> sort.worth
        ItemListOrder.RELEVANCE -> false
    }

    internal fun shows(type: ItemListTypeFilter): Boolean = when (type) {
        ItemListTypeFilter.MOBS -> filters.mobs
        ItemListTypeFilter.PETS -> filters.pets
        ItemListTypeFilter.TOOLS -> filters.tools
        ItemListTypeFilter.ARMOR -> filters.armor
        ItemListTypeFilter.ACCESSORIES -> filters.accessories
        ItemListTypeFilter.ALL -> false
    }

    internal fun isShown(): Boolean = enabled && listOf(
        sort.name,
        sort.rarity,
        sort.worth,
        filters.mobs,
        filters.pets,
        filters.tools,
        filters.armor,
        filters.accessories,
    ).any { it }
}

class ItemListSortButtonsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Name", desc = "Alphabetical order.")
    @field:ConfigEditorBoolean
    var name = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rarity", desc = "Order by rarity.")
    @field:ConfigEditorBoolean
    var rarity = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Item Worth", desc = "Order by bazaar or lowest bin price.")
    @field:ConfigEditorBoolean
    var worth = true
}

class ItemListFilterButtonsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mobs", desc = "Show only mobs.")
    @field:ConfigEditorBoolean
    var mobs = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Pets", desc = "Show only pets.")
    @field:ConfigEditorBoolean
    var pets = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Tools", desc = "Show only tools.")
    @field:ConfigEditorBoolean
    var tools = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Armor", desc = "Show only armor.")
    @field:ConfigEditorBoolean
    var armor = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Accessories", desc = "Show only accessories.")
    @field:ConfigEditorBoolean
    var accessories = true
}

class ItemListSourcesConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    val searchPosition = HudPosition(DEFAULT_SEARCH_OFFSET, DEFAULT_SEARCH_OFFSET, centerY = false).rememberDefault()

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
    var bazaarGraphMode = BazaarGraphMode.PRICE_HISTORY.name

    @JvmField
    @field:Expose
    var bazaarGraphWindow = BazaarGraphWindow.ONE_HOUR.name

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

    override fun repairLoadedValues() {
        searchPosition.scale = HudPosition.DEFAULT_SCALE
        searchWidth = searchWidth.coerceIn(MIN_SEARCH_WIDTH, MAX_SEARCH_WIDTH)
        bazaarGraphMode = graphMode().name
        bazaarGraphWindow = graphWindow().name
    }

    internal fun graphMode(): BazaarGraphMode = BazaarGraphMode.fromStoredName(bazaarGraphMode)

    internal fun graphWindow(): BazaarGraphWindow = BazaarGraphWindow.fromStoredName(bazaarGraphWindow)

    companion object {
        const val DEFAULT_SEARCH_WIDTH = 162
        const val MIN_SEARCH_WIDTH = 72
        const val SEARCH_WIDTH_STEP = 18
        const val MAX_SEARCH_WIDTH = ItemListSettingsConfig.MAX_COLUMNS * SEARCH_WIDTH_STEP * 3

        private const val DEFAULT_SEARCH_OFFSET = -4
    }
}
