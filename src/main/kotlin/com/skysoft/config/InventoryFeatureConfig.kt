package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.HudPosition
import com.skysoft.features.inventory.InventoryButtonEditorScreen
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.SlotLockManager
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property
import org.lwjgl.glfw.GLFW

enum class BazaarTrackerSound(private val displayName: String) {
    FILLED("§bFilled"),
    PARTIAL("§ePartial Fill"),
    OUTBID_UNDERCUT("§6Outbid / Undercut"),
    ;

    override fun toString(): String = displayName
}

class InventoryFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Item List", desc = "Browse items, recipes, and usages.")
    val itemList = ItemListConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Bazaar", desc = "Bazaar order tracking and overlays.")
    val bazaar = SkysoftBazaarConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Tooltip Scroll", desc = "Move oversized item tooltips.")
    val tooltipScroll = TooltipScrollConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Price Tooltips", desc = "Market and craft values on item tooltips.")
    val priceTooltips = PriceTooltipsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Rarity Highlight", desc = "Highlight inventory items by SkyBlock rarity.")
    val rarityHighlight = RarityHighlightConfig()

    @JvmField
    @field:Expose
    val storageOverlay = StorageOverlayConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Solid Tooltip Background", desc = "Make tooltip backgrounds fully opaque.")
    @field:ConfigEditorBoolean
    var isTooltipBackgroundSolid = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Storage Overlay", desc = "Replace SkyBlock storage screens with a searchable overlay.")
    @field:ConfigEditorBoolean
    var isStorageOverlayEnabled = false

    @JvmField
    @field:Expose
    @field:Category(name = "Inventory Equipment", desc = "Show cached equipment beside your inventory.")
    val inventoryEquipment = InventoryEquipmentConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Inventory Buttons", desc = "Custom command buttons shown on inventory screens.")
    val inventoryButtons = InventoryButtonsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Full Inventory", desc = "Warn when your inventory is nearly full.")
    val fullInventory = FullInventoryConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Slot Bindings", desc = "Bind inventory slots together and shift-click either slot to swap them.")
    val slotBindings = SlotBindingsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Slot Locking", desc = "Protect inventory slots from item movement and drops.")
    val slotLocking = SlotLockingConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Protect Item", desc = "Keep specific SkyBlock items from being dropped.")
    val protectItem = ProtectItemConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Smooth Swapping", desc = "Animate items moving between inventory slots.")
    val smoothSwapping = SmoothSwappingConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Vanilla Recipe Book",
        desc = "Hide the vanilla recipe book in your inventory on SkyBlock.",
    )
    @field:ConfigEditorBoolean
    var isVanillaRecipeBookHidden = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Preserve Cursor Position",
        desc = "Keep the mouse at the same position when Minecraft briefly closes and reopens an inventory, " +
            "such as SkyBlock storage page swaps.",
    )
    @field:ConfigEditorBoolean
    var preserveCursorPosition = false

    fun repairLoadedValues() {
        itemList.repairLoadedValues()
        bazaar.repairLoadedValues()
        tooltipScroll.repairLoadedValues()
        priceTooltips.repairLoadedValues()
        smoothSwapping.repairLoadedValues()
        inventoryButtons.repairLoadedValues()
        fullInventory.repairLoadedValues()
        storageOverlay.repairLoadedValues()
        rarityHighlight.repairLoadedValues()
    }
}

class InventoryEquipmentConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show cached equipment beside your inventory.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Click Action", desc = "Choose what happens when clicking an inventory equipment slot.")
    @field:ConfigEditorDropdown
    var clickAction = InventoryEquipmentClickAction.STATS
}

enum class InventoryEquipmentClickAction(private val displayName: String, val command: String?) {
    NOTHING("Nothing", null),
    STATS("/stats", "stats"),
    EQUIPMENT("/equipment", "equipment"),
    LOADOUT("/loadout", "loadout"),
    ;

    override fun toString(): String = displayName
}

class ItemListConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show Skysoft's Item List on Hypixel inventory screens.")
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

    fun repairLoadedValues() {
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
    @field:ConfigOption(name = "Info Key", desc = "Open the hovered item's Item List Info tab.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_I)
    var infoKey = GLFW.GLFW_KEY_I

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Recipes Key", desc = "Open the hovered item's Item List Obtain tab.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_R)
    var recipesKey = GLFW.GLFW_KEY_R

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Uses Key", desc = "Open the hovered item's Item List Uses tab.")
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
        )
    }
}

class RarityHighlightConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight inventory items by rarity.")
    @field:ConfigEditorBoolean
    var isEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Rarity highlight controls.")
    @field:Accordion
    val settings = RarityHighlightSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Rarity highlight appearance.")
    @field:Accordion
    val details = RarityHighlightDetailsConfig()

    fun repairLoadedValues() {
        details.opacity = details.opacity.coerceIn(
            RarityHighlightDetailsConfig.MIN_OPACITY,
            RarityHighlightDetailsConfig.MAX_OPACITY,
        )
    }
}

class RarityHighlightSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Type", desc = "Choose the highlight shape.")
    @field:ConfigEditorDropdown
    var type = RarityHighlightType.SQUARE
}

class RarityHighlightDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Opacity", desc = "Opacity of rarity highlights.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
    var opacity = 40

    companion object {
        const val MIN_OPACITY = 0
        const val MAX_OPACITY = 100
    }
}

enum class RarityHighlightType(private val displayName: String) {
    ROUND("Round"),
    SQUARE("Square"),
    CONTOUR("Contour"),
    ;

    override fun toString(): String = displayName
}

class InventoryButtonsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show custom command buttons on inventory screens.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Inventory button settings.")
    @field:Accordion
    val settings = InventoryButtonsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Inventory button visual settings.")
    @field:Accordion
    val details = InventoryButtonsDetailsConfig()

    @JvmField
    @field:Expose
    var buttons: MutableList<InventoryButtonConfig> = InventoryButtonDefaults.create()

    fun repairLoadedValues() {
        buttons = InventoryButtonDefaults.resettableButtons(buttons)
        buttons.forEachIndexed { index, button ->
            button.repairLoadedValues(isLegacyExtraButton = index >= InventoryButtonDefaults.DEFAULT_BUTTON_COUNT)
        }
    }
}

class InventoryButtonsSettingsConfig {
    @JvmField
    @field:ConfigOption(name = "Open Button Editor", desc = "Open the inventory button editor.")
    @field:ConfigEditorButton(buttonText = "Open")
    val openEditor = Runnable { InventoryButtonEditorScreen.open() }

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Button Click Type", desc = "Choose whether buttons trigger when the mouse is pressed or released.")
    @field:ConfigEditorDropdown
    var clickType = InventoryButtonClickType.MOUSE_DOWN
}

class InventoryButtonsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Tooltip Delay", desc = "Delay before showing a button's command tooltip, in milliseconds.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 1500f, minStep = 50f)
    var tooltipDelay = 600
}

enum class InventoryButtonClickType(private val displayName: String) {
    MOUSE_DOWN("Mouse Down"),
    MOUSE_UP("Mouse Up"),
    ;

    override fun toString(): String = displayName
}

class SlotBindingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Enable slot bindings.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Slot binding controls.")
    @field:Accordion
    val settings = SlotBindingsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Slot binding visual settings.")
    @field:Accordion
    val details = SlotBindingsDetailsConfig()
}

class SlotBindingsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Binding Key",
        desc = "Hold this key over an inventory slot, move to another slot, and release to bind. Tap over a bound slot to unbind.",
    )
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_B)
    var bindingKey = GLFW.GLFW_KEY_B

    @JvmField
    @field:ConfigOption(name = "Reset All Bindings", desc = "Remove all slot bindings.")
    @field:ConfigEditorButton(buttonText = "Reset")
    val resetAllBindings = Runnable { SlotBindingManager.resetAllBindings() }
}

class SlotBindingsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Highlights", desc = "Draw highlights and lines for bound slots.")
    @field:ConfigEditorBoolean
    var showHighlights = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Color", desc = "Color used for bound slot outlines and lines.")
    @field:ConfigEditorColour
    val highlightColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(48, 128, 255, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Style", desc = "Choose whether bound slots are filled or only outlined.")
    @field:ConfigEditorDropdown
    var highlightStyle = SlotBindingHighlightStyle.FILL

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Shift Hover Highlight",
        desc = "While holding Shift over a bound slot, highlight its paired slot in white.",
    )
    @field:ConfigEditorBoolean
    var showShiftHoverHighlight = true
}

class SlotLockingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Protect locked inventory slots.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Slot locking controls.")
    @field:Accordion
    val settings = SlotLockingSettingsConfig()

    val lockKey: Int
        get() = settings.lockKey
}

class SlotLockingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Lock Key", desc = "Press this key while hovering an inventory slot to lock or unlock it.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_L)
    var lockKey = GLFW.GLFW_KEY_L

    @JvmField
    @field:ConfigOption(name = "Reset All Locks", desc = "Unlock every inventory slot on the current SkyBlock profile.")
    @field:ConfigEditorButton(buttonText = "Reset")
    val resetAllLocks = Runnable { SlotLockManager.resetAllLocks() }
}

class ProtectItemConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Prevent protected items from being dropped.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Item protection controls.")
    @field:Accordion
    val settings = ProtectItemSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Protected item visual details.")
    @field:Accordion
    val details = ProtectItemDetailsConfig()
}

class ProtectItemSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Protect Key", desc = "Press this key while hovering an inventory item to protect or unprotect it.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var protectKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Allow Dungeon Ultimates",
        desc = "Let the drop key activate ultimates while holding protected items in Dungeons.",
    )
    @field:ConfigEditorBoolean
    var allowDungeonUltimates = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Drop Messages", desc = "Hide chat messages when a protected item drop is blocked.")
    @field:ConfigEditorBoolean
    var hideProtectedItemDropMessages = false

    @JvmField
    @field:ConfigOption(name = "Reset Protected Items", desc = "Unprotect every item on the current SkyBlock profile.")
    @field:ConfigEditorButton(buttonText = "Reset")
    val resetProtectedItems = Runnable { ItemProtectionManager.resetProtectedItems() }
}

class ProtectItemDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Protected Item Star", desc = "Show a small star on protected items.")
    @field:ConfigEditorBoolean
    var showProtectedItemStar = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Star Color", desc = "Color used for protected item stars.")
    @field:ConfigEditorColour
    @field:ConfigVisibleIf("showProtectedItemStar")
    val protectedItemStarColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(255, 213, 79, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Star Size", desc = "Size of protected item stars.")
    @field:ConfigEditorSlider(minValue = 0.5f, maxValue = 1.5f, minStep = 0.1f)
    @field:ConfigVisibleIf("showProtectedItemStar")
    var protectedItemStarScale = 1f

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Star Opacity", desc = "Opacity of protected item stars.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
    @field:ConfigVisibleIf("showProtectedItemStar")
    var protectedItemStarOpacity = 100
}

enum class SlotBindingHighlightStyle(private val displayName: String) {
    FILL("Fill"),
    EDGES("Edges"),
    ;

    override fun toString(): String = displayName
}

class FullInventoryConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show a warning when your inventory reaches the configured empty slot limit.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Full inventory warning settings.")
    @field:Accordion
    val settings = FullInventorySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Full inventory alert details.")
    @field:Accordion
    val details = FullInventoryDetailsConfig()

    fun repairLoadedValues() {
        settings.emptySlots = settings.emptySlots.coerceIn(
            MIN_FULL_INVENTORY_EMPTY_SLOTS,
            MAX_FULL_INVENTORY_EMPTY_SLOTS,
        )
    }
}

class FullInventorySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Empty Slots",
        desc = "Warn when your inventory has this many empty slots or less. 0 means only when full.",
    )
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 36f, minStep = 1f)
    var emptySlots = DEFAULT_FULL_INVENTORY_EMPTY_SLOTS
}

class FullInventoryDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Play Sound", desc = "Play a sound when the warning triggers.")
    @field:ConfigEditorBoolean
    var playSound = true
}

class SmoothSwappingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Animate item movement inside inventory screens.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Item movement animation settings.")
    @field:Accordion
    val settings = SmoothSwappingSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Item movement animation details.")
    @field:Accordion
    val details = SmoothSwappingDetailsConfig()

    fun repairLoadedValues() {
        settings.animationSpeed = settings.animationSpeed.coerceIn(
            MIN_SMOOTH_SWAPPING_SPEED,
            MAX_SMOOTH_SWAPPING_SPEED,
        )
    }
}

class SmoothSwappingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Animation Speed", desc = "How quickly items move between slots. 100 matches the default speed.")
    @field:ConfigEditorSlider(minValue = 25f, maxValue = 300f, minStep = 5f)
    var animationSpeed = DEFAULT_SMOOTH_SWAPPING_SPEED
}

class SmoothSwappingDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Animation Curve", desc = "Choose how item movement accelerates and slows down.")
    @field:ConfigEditorDropdown
    var animationCurve = SmoothSwappingCurve.EASE_IN_OUT
}

enum class SmoothSwappingCurve(private val displayName: String) {
    LINEAR("Linear"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In Out"),
    ;

    override fun toString(): String = displayName
}

class TooltipScrollConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Allow tooltips to be moved with the mouse wheel and movement keys.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Tooltip movement controls.")
    @field:Accordion
    val settings = TooltipScrollSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Tooltip movement behavior and animation.")
    @field:Accordion
    val details = TooltipScrollDetailsConfig()

    fun repairLoadedValues() {
        settings.mouseScrollingSpeed = settings.mouseScrollingSpeed.coerceIn(
            MIN_TOOLTIP_SCROLL_SPEED,
            MAX_TOOLTIP_SCROLL_SPEED,
        )
        settings.keyboardScrollingSpeed = settings.keyboardScrollingSpeed.coerceIn(
            MIN_TOOLTIP_SCROLL_SPEED,
            MAX_TOOLTIP_SCROLL_SPEED,
        )
        details.scrollSmoothness = details.scrollSmoothness.coerceIn(
            MIN_TOOLTIP_SCROLL_SMOOTHNESS,
            MAX_TOOLTIP_SCROLL_SMOOTHNESS,
        )
    }
}

class TooltipScrollSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enable Scroll Wheel", desc = "Move tooltips with the mouse wheel.")
    @field:ConfigEditorBoolean
    var enableScrollWheel = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enable in Chat", desc = "Allow tooltip movement while chat is open.")
    @field:ConfigEditorBoolean
    var isEnabledInChat = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Storage Overlay Tooltip Key",
        desc = "Hold this key to scroll a tooltip instead of the Storage Overlay.",
    )
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT_SHIFT)
    var storageOverlayTooltipKey = GLFW.GLFW_KEY_LEFT_SHIFT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enable WASD", desc = "Use WASD to move the hovered tooltip.")
    @field:ConfigEditorBoolean
    var enableWASD = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mouse Scrolling Speed", desc = "Pixels moved per mouse-wheel step.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var mouseScrollingSpeed = DEFAULT_TOOLTIP_SCROLL_SPEED

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Keyboard Scrolling Speed", desc = "Pixels moved per tick while a tooltip movement key is held.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var keyboardScrollingSpeed = DEFAULT_TOOLTIP_KEYBOARD_SCROLL_SPEED

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Move Up Key", desc = "Move the hovered tooltip up.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_PAGE_UP)
    var moveUpKey = GLFW.GLFW_KEY_PAGE_UP

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Move Down Key", desc = "Move the hovered tooltip down.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_PAGE_DOWN)
    var moveDownKey = GLFW.GLFW_KEY_PAGE_DOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Horizontal Movement Key", desc = "Hold this key to make up and down movement horizontal.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var horizontalMovementKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reset Tooltip Key", desc = "Reset the hovered tooltip's moved position.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var resetTooltipKey = GLFW.GLFW_KEY_UNKNOWN
}

class TooltipScrollDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Start On Top", desc = "Show the top of oversized tooltips when they first appear.")
    @field:ConfigEditorBoolean
    var startOnTop = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reset Position When Not Hovered", desc = "Reset tooltip movement after the tooltip disappears.")
    @field:ConfigEditorBoolean
    var resetPositionWhenNotHovered = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Use Left Shift", desc = "Hold left shift to move tooltips horizontally with the mouse wheel.")
    @field:ConfigEditorBoolean
    var useLeftShift = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Invert Horizontal Movement", desc = "Invert horizontal tooltip movement.")
    @field:ConfigEditorBoolean
    var invertHorizontalMovement = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Invert Vertical Movement", desc = "Invert vertical tooltip movement.")
    @field:ConfigEditorBoolean
    var invertVerticalMovement = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Scroll Smoothness", desc = "How quickly tooltips slide toward the moved position. 100 is instant.")
    @field:ConfigEditorSlider(minValue = 5f, maxValue = 100f, minStep = 5f)
    var scrollSmoothness = DEFAULT_TOOLTIP_SCROLL_SMOOTHNESS
}

class InventoryButtonConfig(
    @JvmField @field:Expose var x: Int = 0,
    @JvmField @field:Expose var y: Int = 0,
    @JvmField @field:Expose var icon: String? = null,
    @JvmField @field:Expose var playerInvOnly: Boolean = false,
    @JvmField @field:Expose var anchorRight: Boolean = false,
    @JvmField @field:Expose var anchorBottom: Boolean = false,
    @JvmField @field:Expose var backgroundIndex: Int = 0,
    @JvmField @field:Expose var command: String = "",
    @JvmField @field:Expose var scale: Float = DEFAULT_INVENTORY_BUTTON_SCALE,
    @JvmField @field:Expose var isUserCreated: Boolean? = null,
) {
    fun isActive(): Boolean = command.trim().isNotEmpty()

    fun repairLoadedValues(isLegacyExtraButton: Boolean = false) {
        backgroundIndex = backgroundIndex.coerceIn(MIN_BUTTON_BACKGROUND_INDEX, MAX_BUTTON_BACKGROUND_INDEX)
        command = command.trimStart()
        icon = icon?.trim()?.takeIf { it.isNotEmpty() }
        scale = scale
            .takeIf(Float::isFinite)
            ?.takeIf { it > 0f }
            ?.coerceIn(MIN_INVENTORY_BUTTON_SCALE, MAX_INVENTORY_BUTTON_SCALE)
            ?: DEFAULT_INVENTORY_BUTTON_SCALE
        isUserCreated = isUserCreated ?: isLegacyExtraButton
    }
}

class StorageOverlayConfig {
    @JvmField
    @field:Expose
    val settings = StorageOverlaySettingsConfig()

    @JvmField
    @field:Expose
    val details = StorageOverlayDetailsConfig()

    fun repairLoadedValues() {
        details.columns = details.columns.coerceIn(
            StorageOverlayConfigBounds.MIN_COLUMNS,
            StorageOverlayConfigBounds.MAX_COLUMNS,
        )
        details.height = details.height.coerceIn(
            StorageOverlayConfigBounds.MIN_HEIGHT,
            StorageOverlayConfigBounds.MAX_HEIGHT,
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
    var miniMenu = true

    @JvmField
    @field:Expose
    var isAutofocusEnabled = true
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
    var scrollSpeed = 18
}

internal object StorageOverlayConfigBounds {
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 5
    const val MIN_HEIGHT = 96
    const val MAX_HEIGHT = 360
    const val HEIGHT_STEP = 18
    const val MIN_SCROLL_SPEED = 1
    const val MAX_SCROLL_SPEED = 40
}

class PriceTooltipsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show configured prices on item tooltips.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Price tooltip settings.")
    @field:Accordion
    val settings = PriceTooltipsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Price tooltip appearance.")
    @field:Accordion
    val details = PriceTooltipsDetailsConfig()

    fun repairLoadedValues() {
        settings.repairLoadedValues()
    }
}

class PriceTooltipsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Require Key", desc = "Only show price lines while the hotkey is held.")
    @field:ConfigEditorBoolean
    var requireKey = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hotkey", desc = "Hold this key to show price lines when Require Key is enabled.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT_SHIFT)
    var hotkey = GLFW.GLFW_KEY_LEFT_SHIFT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Price Lines", desc = "Choose and order the prices shown in item tooltips.")
    @field:ConfigEditorDraggableList
    val priceLines: Property<MutableList<PriceTooltipLine>> = Property.of(defaultPriceTooltipLines())

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bazaar Wording", desc = "Use Order or Offer names for bazaar order prices.")
    @field:ConfigEditorDropdown
    var bazaarWording = BazaarTooltipWording.ORDERS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Stack Total Key", desc = "Hold this key to show prices for the full stack.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT_SHIFT)
    var stackTotalKey = GLFW.GLFW_KEY_LEFT_SHIFT

    @JvmField
    @field:Expose(serialize = false, deserialize = true)
    var legacyBazaarPriceType: BazaarPriceType? = null

    fun repairLoadedValues() {
        legacyBazaarPriceType?.let { legacyType ->
            priceLines.get().apply {
                clear()
                addAll(
                    when (legacyType) {
                        BazaarPriceType.ORDER_PRICES -> defaultPriceTooltipLines()
                        BazaarPriceType.INSTANT_PRICES -> mutableListOf(
                            PriceTooltipLine.BAZAAR_INSTANT_BUY,
                            PriceTooltipLine.BAZAAR_INSTANT_SELL,
                            PriceTooltipLine.LOWEST_BIN,
                        )
                    },
                )
            }
            legacyBazaarPriceType = null
        }
        val uniqueLines = priceLines.get().distinct()
        priceLines.get().apply {
            clear()
            addAll(uniqueLines)
        }
    }
}

class PriceTooltipsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bold Text", desc = "Show price tooltip text in bold.")
    @field:ConfigEditorBoolean
    var boldText = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Label Color", desc = "Color used for price labels.")
    @field:ConfigEditorColour
    val labelColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Price Color", desc = "Color used for price values.")
    @field:ConfigEditorColour
    val priceColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 170, 0, 0, 255))
}

enum class PriceTooltipLine(private val displayName: String) {
    BAZAAR_BUY_ORDER("Bazaar Buy Order"),
    BAZAAR_SELL_ORDER("Bazaar Sell Order"),
    BAZAAR_INSTANT_BUY("Bazaar Instant Buy"),
    BAZAAR_INSTANT_SELL("Bazaar Instant Sell"),
    LOWEST_BIN("Lowest BIN"),
    NPC_SELL_PRICE("NPC Sell Price"),
    RAW_CRAFT_COST("Raw Craft Cost"),
    ;

    val needsBazaarData: Boolean
        get() = this in BAZAAR_LINES || this == RAW_CRAFT_COST

    val needsLowestBinData: Boolean
        get() = this == LOWEST_BIN || this == RAW_CRAFT_COST

    override fun toString(): String = displayName

    companion object {
        private val BAZAAR_LINES = setOf(
            BAZAAR_BUY_ORDER,
            BAZAAR_SELL_ORDER,
            BAZAAR_INSTANT_BUY,
            BAZAAR_INSTANT_SELL,
        )
    }
}

enum class BazaarTooltipWording(private val displayName: String) {
    ORDERS("Orders"),
    OFFERS("Offers"),
    ;

    override fun toString(): String = displayName
}

enum class BazaarPriceType(private val displayName: String) {
    ORDER_PRICES("Order Prices"),
    INSTANT_PRICES("Instant Prices"),
    ;

    override fun toString(): String = displayName
}

class SkysoftBazaarConfig {
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

    fun repairLoadedValues() {
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

private fun defaultPriceTooltipLines(): MutableList<PriceTooltipLine> = mutableListOf(
    PriceTooltipLine.BAZAAR_BUY_ORDER,
    PriceTooltipLine.BAZAAR_SELL_ORDER,
    PriceTooltipLine.LOWEST_BIN,
)

class BazaarTrackerDetailsConfig {
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

const val MIN_FULL_INVENTORY_EMPTY_SLOTS = 0
const val MAX_FULL_INVENTORY_EMPTY_SLOTS = 36
const val DEFAULT_FULL_INVENTORY_EMPTY_SLOTS = 0
const val MIN_TOOLTIP_SCROLL_SPEED = 1
const val MAX_TOOLTIP_SCROLL_SPEED = 40
const val DEFAULT_TOOLTIP_SCROLL_SPEED = 10
const val DEFAULT_TOOLTIP_KEYBOARD_SCROLL_SPEED = 5
const val MIN_TOOLTIP_SCROLL_SMOOTHNESS = 5
const val MAX_TOOLTIP_SCROLL_SMOOTHNESS = 100
const val DEFAULT_TOOLTIP_SCROLL_SMOOTHNESS = 25
const val MIN_SMOOTH_SWAPPING_SPEED = 25
const val MAX_SMOOTH_SWAPPING_SPEED = 300
const val DEFAULT_SMOOTH_SWAPPING_SPEED = 125
const val DEFAULT_SMOOTH_SWAPPING_DURATION = 180
const val MIN_INVENTORY_BUTTON_SCALE = 0.5f
const val MAX_INVENTORY_BUTTON_SCALE = 3f
const val DEFAULT_INVENTORY_BUTTON_SCALE = 1f
const val INVENTORY_BUTTON_SCALE_STEP = 0.1f
private const val MIN_BUTTON_BACKGROUND_INDEX = 0
private const val MAX_BUTTON_BACKGROUND_INDEX = 6
private const val MIN_BAZAAR_TRACKER_ORDERS = 1
private const val MAX_BAZAAR_TRACKER_ORDERS = 20
