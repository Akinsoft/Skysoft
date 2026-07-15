package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.HudPosition
import com.skysoft.features.inventory.InventoryButtonEditorScreen
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
    @field:Category(name = "Price Tooltips", desc = "Bazaar and lowest BIN item prices.")
    val priceTooltips = PriceTooltipsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Storage Overlay", desc = "Searchable SkyBlock storage screens.")
    val storageOverlay = StorageOverlayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Inventory Equipment", desc = "Show cached equipment beside the player inventory.")
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
    @field:Category(name = "Smooth Swapping", desc = "Animate items moving between inventory slots.")
    val smoothSwapping = SmoothSwappingConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Preserve Cursor Position",
        desc = "Keep the mouse at the same position when Minecraft briefly closes and reopens an inventory, " +
            "such as SkyBlock storage page swaps.",
    )
    @field:ConfigEditorBoolean
    var preserveCursorPosition = true

    fun repairLoadedValues() {
        itemList.repairLoadedValues()
        bazaar.repairLoadedValues()
        tooltipScroll.repairLoadedValues()
        smoothSwapping.repairLoadedValues()
        inventoryButtons.repairLoadedValues()
        fullInventory.repairLoadedValues()
        storageOverlay.repairLoadedValues()
    }
}

class ItemListConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show Skysoft's Item List on Hypixel inventory screens.")
    @field:ConfigEditorBoolean
    var enabled = true

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

    @JvmField
    @field:Expose
    var recentItems: MutableList<String> = mutableListOf()

    fun repairLoadedValues() {
        favorites = favorites.filter(String::isNotBlank).distinct().take(MAX_FAVORITES).toMutableList()
        recentItems = recentItems.filter(String::isNotBlank).distinct().take(MAX_RECENT_ITEMS).toMutableList()
        sources.searchPosition.rememberDefault(ItemListSourcesConfig.defaultSearchPosition())
        settings.repairLoadedValues()
        sources.repairLoadedValues()
    }

    companion object {
        const val MAX_FAVORITES = 64
        const val MAX_RECENT_ITEMS = 40
    }
}

class ItemListSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Items Per Row", desc = "Maximum items shown horizontally.")
    @field:ConfigEditorSlider(minValue = 2f, maxValue = 32f, minStep = 1f)
    var columns = DEFAULT_COLUMNS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rows", desc = "Maximum item rows shown vertically. 0 fills the available height.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 32f, minStep = 1f)
    var rows = DEFAULT_ROWS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Visibility Key", desc = "Temporarily show or hide Item List.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_KP_SUBTRACT)
    var visibilityKey = GLFW.GLFW_KEY_KP_SUBTRACT

    @JvmField
    @field:ConfigOption(name = "Refresh Data", desc = "Check for updated Item List data.")
    @field:ConfigEditorButton(buttonText = "Refresh")
    val refreshData = Runnable { com.skysoft.data.skyblock.SkyBlockDataRepository.reload() }

    @JvmField
    @field:ConfigOption(name = "Clear Recent Items", desc = "Clear Item List history.")
    @field:ConfigEditorButton(buttonText = "Clear")
    val clearRecent = Runnable {
        SkysoftConfigGui.config().inventory.itemList.recentItems.clear()
        SkysoftConfigGui.config().saveNow()
    }

    @JvmField
    @field:ConfigOption(name = "Clear Favorites", desc = "Clear all favorite items.")
    @field:ConfigEditorButton(buttonText = "Clear")
    val clearFavorites = Runnable {
        SkysoftConfigGui.config().inventory.itemList.favorites.clear()
        SkysoftConfigGui.config().saveNow()
    }

    fun repairLoadedValues() {
        columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        rows = rows.coerceIn(MIN_ROWS, MAX_ROWS)
    }

    companion object {
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
    @field:ConfigOption(name = "Vanilla Items", desc = "Include Minecraft items.")
    @field:ConfigEditorBoolean
    var showVanilla = false

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
        bazaarGraphMode = when (bazaarGraphMode) {
            "PRICE" -> "ORDER_BOOK"
            "ACTIVITY" -> "TRADE_VOLUME"
            in BAZAAR_GRAPH_MODES -> bazaarGraphMode
            else -> "PRICE_HISTORY"
        }
        if (bazaarGraphWindow !in BAZAAR_GRAPH_WINDOWS) bazaarGraphWindow = "ONE_HOUR"
    }

    companion object {
        fun defaultSearchPosition() = HudPosition(DEFAULT_SEARCH_OFFSET, DEFAULT_SEARCH_OFFSET, centerY = false)

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

class InventoryEquipmentConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show cached equipment beside your inventory.")
    @field:ConfigEditorBoolean
    var enabled = true
}

class InventoryButtonsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show custom command buttons on inventory screens.")
    @field:ConfigEditorBoolean
    var enabled = true

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
        buttons.forEach { it.repairLoadedValues() }
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
    var enabled = true

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
    var enabled = true

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
    var enabled = true

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
            FullInventoryLimits.MIN_EMPTY_SLOTS,
            FullInventoryLimits.MAX_EMPTY_SLOTS,
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
    var emptySlots = FullInventoryLimits.DEFAULT_EMPTY_SLOTS
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
            SmoothSwappingLimits.MIN_SPEED,
            SmoothSwappingLimits.MAX_SPEED,
        )
    }
}

class SmoothSwappingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Animation Speed", desc = "How quickly items move between slots. 100 matches the default speed.")
    @field:ConfigEditorSlider(minValue = 25f, maxValue = 300f, minStep = 5f)
    var animationSpeed = SmoothSwappingLimits.DEFAULT_SPEED
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
    var enabled = true

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
            TooltipScrollLimits.MIN_SPEED,
            TooltipScrollLimits.MAX_SPEED,
        )
        settings.keyboardScrollingSpeed = settings.keyboardScrollingSpeed.coerceIn(
            TooltipScrollLimits.MIN_SPEED,
            TooltipScrollLimits.MAX_SPEED,
        )
        details.scrollSmoothness = details.scrollSmoothness.coerceIn(
            TooltipScrollLimits.MIN_SMOOTHNESS,
            TooltipScrollLimits.MAX_SMOOTHNESS,
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
    @field:ConfigOption(name = "Enable WASD", desc = "Use WASD to move the hovered tooltip.")
    @field:ConfigEditorBoolean
    var enableWASD = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mouse Scrolling Speed", desc = "Pixels moved per mouse-wheel step.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var mouseScrollingSpeed = TooltipScrollLimits.DEFAULT_MOUSE_SPEED

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Keyboard Scrolling Speed", desc = "Pixels moved per tick while a tooltip movement key is held.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var keyboardScrollingSpeed = TooltipScrollLimits.DEFAULT_KEYBOARD_SPEED

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
    var scrollSmoothness = TooltipScrollLimits.DEFAULT_SMOOTHNESS
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
) {
    fun isActive(): Boolean = command.trim().isNotEmpty()

    fun repairLoadedValues() {
        backgroundIndex = backgroundIndex.coerceIn(InventoryButtonLimits.MIN_BACKGROUND_INDEX, InventoryButtonLimits.MAX_BACKGROUND_INDEX)
        command = command.trimStart()
        icon = icon?.trim()?.takeIf { it.isNotEmpty() }
    }
}

class StorageOverlayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Replace Hypixel SkyBlock storage screens with a searchable overlay.")
    @field:ConfigEditorBoolean
    var enabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Storage overlay settings.")
    @field:Accordion
    val settings = StorageOverlaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Storage overlay layout settings.")
    @field:Accordion
    val details = StorageOverlayDetailsConfig()

    fun repairLoadedValues() {
        details.columns = details.columns.coerceIn(StorageOverlayLimits.MIN_COLUMNS, StorageOverlayLimits.MAX_COLUMNS)
        details.height = details.height.coerceIn(StorageOverlayLimits.MIN_HEIGHT, StorageOverlayLimits.MAX_HEIGHT)
        details.scrollSpeed = details.scrollSpeed.coerceIn(
            StorageOverlayLimits.MIN_SCROLL_SPEED,
            StorageOverlayLimits.MAX_SCROLL_SPEED,
        )
    }
}

class StorageOverlaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Shortcut", desc = "Show the storage overview shortcut next to the player inventory.")
    @field:ConfigEditorBoolean
    var miniMenu = true
}

class StorageOverlayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Columns", desc = "Storage pages shown per row.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 5f, minStep = 1f)
    var columns = 3

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Height", desc = "Height of the scrollable storage page area.")
    @field:ConfigEditorSlider(minValue = 96f, maxValue = 360f, minStep = 18f)
    var height = 234

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Scroll Speed", desc = "Rows scrolled per mouse-wheel step.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var scrollSpeed = 18
}

class PriceTooltipsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show bazaar and lowest BIN prices on item tooltips.")
    @field:ConfigEditorBoolean
    var enabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Price tooltip settings.")
    @field:Accordion
    val settings = PriceTooltipsSettingsConfig()
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
    @field:ConfigOption(name = "Bazaar Price Type", desc = "Choose whether bazaar tooltip prices use orders or instant prices.")
    @field:ConfigEditorDropdown
    var bazaarPriceType = BazaarPriceType.ORDER_PRICES
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
    var enabled = true

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
            BazaarTrackerConfigLimits.MIN_ORDERS,
            BazaarTrackerConfigLimits.MAX_ORDERS,
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
}

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
    var showBackground = true

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

object FullInventoryLimits {
    const val MIN_EMPTY_SLOTS = 0
    const val MAX_EMPTY_SLOTS = 36
    const val DEFAULT_EMPTY_SLOTS = 0
}

object SmoothSwappingLimits {
    const val MIN_SPEED = 25
    const val MAX_SPEED = 300
    const val DEFAULT_SPEED = 125
    const val DEFAULT_DURATION = 180
}

private object TooltipScrollLimits {
    const val MIN_SPEED = 1
    const val MAX_SPEED = 40
    const val DEFAULT_MOUSE_SPEED = 10
    const val DEFAULT_KEYBOARD_SPEED = 5
    const val MIN_SMOOTHNESS = 5
    const val MAX_SMOOTHNESS = 100
    const val DEFAULT_SMOOTHNESS = 25
}

private object InventoryButtonLimits {
    const val MIN_BACKGROUND_INDEX = 0
    const val MAX_BACKGROUND_INDEX = 6
}

private object StorageOverlayLimits {
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 5
    const val MIN_HEIGHT = 96
    const val MAX_HEIGHT = 360
    const val MIN_SCROLL_SPEED = 1
    const val MAX_SCROLL_SPEED = 40
}

private object BazaarTrackerConfigLimits {
    const val MIN_ORDERS = 1
    const val MAX_ORDERS = 20
}
