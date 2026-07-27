package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairLoadedConfigs
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class InventoryFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:Category(name = "Item List", desc = "Browse items, mobs, recipes, drops, and usages.")
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
    @field:Category(name = "Item Change Log", desc = "Show recent inventory item gains and losses.")
    val itemChangeLog = ItemChangeLogConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Rarity Highlight", desc = "Highlight inventory items by SkyBlock rarity.")
    val rarityHighlight = RarityHighlightConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Storage Previews", desc = "Preview the contents of SkyBlock storage items.")
    val storagePreviews = StoragePreviewsConfig()

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
    @field:Category(name = "Max Enchant Chroma", desc = "Show maximum-level enchantments in animated chroma.")
    val maxEnchantChroma = MaxEnchantChromaConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Minister in Calendar", desc = "Show the current minister and perk beside the mayor tooltip.")
    @field:ConfigEditorBoolean
    var isMinisterInCalendarShown = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Experimentation Helper",
        desc = "Show Chronomatron notes, reveal Ultrasequencer numbers, and keep seen Superpairs rewards visible.",
    )
    @field:ConfigEditorBoolean
    var isExperimentationTableHelperEnabled = false

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
        name = "Preserve Cursor Position",
        desc = "Keep the mouse at the same position when Minecraft briefly closes and reopens an inventory, " +
            "such as SkyBlock storage page swaps.",
    )
    @field:ConfigEditorBoolean
    var preserveCursorPosition = false

    override fun repairLoadedValues() = repairLoadedConfigs(
        itemList,
        bazaar,
        tooltipScroll,
        priceTooltips,
        smoothSwapping,
        inventoryButtons,
        fullInventory,
        storagePreviews,
        storageOverlay,
        rarityHighlight,
    )
}

class MaxEnchantChromaConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show maximum-level enchantments in animated chroma.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Max Enchant Chroma settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = MaxEnchantChromaSettingsConfig()
}

class MaxEnchantChromaSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ultimate Enchantments", desc = "Apply chroma to maximum-level Ultimate Enchantments.")
    @field:ConfigEditorBoolean
    var includeUltimateEnchantments = false
}
