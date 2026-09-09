package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class InventoryEquipmentGroupConfig {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Inventory Equipment", desc = "Show cached equipment beside your inventory.")
    val inventoryEquipment = InventoryEquipmentConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Wardrobe Keybinds", desc = "Equip armor sets with keys while the Armor Sets menu is open.")
    val wardrobeKeybinds = SetMenuKeybindsConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Equipment Keybinds", desc = "Equip equipment sets with keys while the Equipment Sets menu is open.")
    val equipmentKeybinds = SetMenuKeybindsConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Loadout Keybinds", desc = "Equip loadouts with keys while the Loadouts menu is open.")
    val loadoutKeybinds = LoadoutMenuKeybindsConfig()
}

class InventoryControlsConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Inventory Buttons", desc = "Custom command buttons shown on inventory screens.")
    val inventoryButtons = InventoryButtonsConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Slot Bindings", desc = "Bind inventory slots together and shift-click either slot to swap them.")
    val slotBindings = SlotBindingsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Smooth Swapping", desc = "Animate items moving between inventory slots.")
    val smoothSwapping = SmoothSwappingConfig()

    @JvmField
    @field:Expose
    @field:Category(
        name = "Preserve Cursor Position",
        desc = "Keep cursor positions through brief inventory closures.",
    )
    val cursorPositionPreservation = PreserveCursorPositionConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Input Math",
        desc = "Calculate equations in SkyBlock number inputs when pressing Enter or Done.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var inputMath = false

    override fun repairLoadedValues() = repairLoadedConfigs(inventoryButtons, smoothSwapping)
}

class InventoryProtectionConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Slot Locking", desc = "Protect inventory slots from item movement and drops.")
    val slotLocking = SlotLockingConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Protect Item", desc = "Keep specific SkyBlock items from being dropped.")
    val protectItem = ProtectItemConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Full Inventory", desc = "Warn when your inventory is nearly full.")
    val fullInventory = FullInventoryConfig()

    override fun repairLoadedValues() = repairLoadedConfigs(fullInventory)
}

class InventoryAppearanceConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Rarity Highlight", desc = "Highlight inventory items by SkyBlock rarity.")
    val rarityHighlight = RarityHighlightConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Head Display Size", desc = "Resize player head item icons.")
    val headDisplaySize = HeadDisplaySizeConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Inventory & Tooltip Scale", desc = "GUI scaling for inventory screens and tooltips.")
    val inventoryScreen = InventoryScreenConfig()

    override fun repairLoadedValues() = repairLoadedConfigs(rarityHighlight, headDisplaySize)
}

class InventoryTooltipsConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:Category(name = "Tooltip Scroll", desc = "Move oversized item tooltips.")
    val tooltipScroll = TooltipScrollConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Solid Tooltip Background", desc = "Make tooltip backgrounds fully opaque.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var isTooltipBackgroundSolid = false

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Max Enchant Chroma", desc = "Show maximum-level enchantments in animated chroma.")
    val maxEnchantChroma = MaxEnchantChromaConfig()

    override fun repairLoadedValues() = repairLoadedConfigs(tooltipScroll)
}
