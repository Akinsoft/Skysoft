package com.skysoft.config

import com.google.gson.JsonObject
import java.lang.reflect.Field

internal object ConfigCategoryLayoutMigration {
    private val paths = mapOf(
        "inventory.inventoryEquipment" to "inventory.equipment.inventoryEquipment",
        "inventory.wardrobeKeybinds" to "inventory.equipment.wardrobeKeybinds",
        "inventory.equipmentKeybinds" to "inventory.equipment.equipmentKeybinds",
        "inventory.loadoutKeybinds" to "inventory.equipment.loadoutKeybinds",
        "inventory.inventoryButtons" to "inventory.controls.inventoryButtons",
        "inventory.slotBindings" to "inventory.controls.slotBindings",
        "inventory.smoothSwapping" to "inventory.controls.smoothSwapping",
        "inventory.cursorPositionPreservation" to "inventory.controls.cursorPositionPreservation",
        "misc.inputMath" to "inventory.controls.inputMath",
        "inventory.slotLocking" to "inventory.protection.slotLocking",
        "inventory.protectItem" to "inventory.protection.protectItem",
        "inventory.fullInventory" to "inventory.protection.fullInventory",
        "inventory.rarityHighlight" to "inventory.appearance.rarityHighlight",
        "inventory.headDisplaySize" to "inventory.appearance.headDisplaySize",
        "gui.inventoryScreen" to "inventory.appearance.inventoryScreen",
        "inventory.tooltipScroll" to "inventory.tooltips.tooltipScroll",
        "inventory.isTooltipBackgroundSolid" to "inventory.tooltips.isTooltipBackgroundSolid",
        "inventory.maxEnchantChroma" to "inventory.tooltips.maxEnchantChroma",
        "inventory.itemList" to "items.itemList",
        "inventory.craftingHelper" to "items.craftingHelper",
        "inventory.bazaar" to "items.bazaar",
        "inventory.priceTooltips" to "items.priceTooltips",
        "inventory.storageOverlay" to "storageFeatures.storageOverlay",
        "inventory.storagePreviews" to "storageFeatures.storagePreviews",
        "inventory.sackDisplay" to "storageFeatures.sackDisplay",
        "inventory.sackHud" to "storageFeatures.sackHud",
        "chat.messageFiltering.hideSacksMessages" to "storageFeatures.hideSacksMessages",
        "inventory.isExperimentationTableHelperEnabled" to "enchanting.isExperimentationTableHelperEnabled",
        "inventory.isMinisterInCalendarShown" to "events.isMinisterInCalendarShown",
        "gui.selectedItemName" to "gui.playerHud.selectedItemName",
        "gui.actionBar" to "gui.playerHud.actionBar",
        "gui.skillExpDisplay" to "gui.playerHud.skillExpDisplay",
        "gui.inventoryHud" to "gui.playerHud.inventoryHud",
        "gui.customBars" to "gui.playerHud.customBars",
        "gui.isSkyBlockLevelBarEnabled" to "gui.playerHud.isSkyBlockLevelBarEnabled",
        "gui.partyDisplay" to "gui.information.partyDisplay",
        "gui.dayDisplay" to "gui.information.dayDisplay",
        "gui.spotifyDisplay" to "gui.information.spotifyDisplay",
        "gui.realTimeDisplay" to "gui.information.realTimeDisplay",
        "gui.serverInfoDisplay" to "gui.information.serverInfoDisplay",
        "gui.crosshairVisibility" to "gui.vanillaUi.crosshairVisibility",
        "gui.areAbsorptionHeartsMerged" to "gui.vanillaUi.areAbsorptionHeartsMerged",
        "gui.isHeartBobbingDisabled" to "gui.vanillaUi.isHeartBobbingDisabled",
        "misc.hideSillyButtons" to "gui.vanillaUi.hideSillyButtons",
        "gui.heldItem" to "world.heldItem",
        "misc.zoom" to "world.zoom",
        "misc.blockOverlay" to "world.blockOverlay",
        "misc.skyColor" to "world.skyColor",
        "misc.droppedItemScaling" to "world.droppedItemScaling",
        "misc.hideDeadEntities" to "world.hideDeadEntities",
        "misc.keepTerrainLoaded" to "world.keepTerrainLoaded",
        "misc.keepSkyBlockResourcePack" to "world.keepSkyBlockResourcePack",
        "profitTrackers" to "loot.profitTrackers",
        "inventory.itemChangeLog" to "loot.itemChangeLog",
        "misc.rareDropTitles" to "loot.rareDropTitles",
        "misc.rareLootSharing" to "loot.rareLootSharing",
        "misc.autoSprint" to "utilities.autoSprint",
        "misc.shortWarpCommands" to "utilities.shortWarpCommands",
        "gui.screenshotManager" to "utilities.screenshotManager",
        "settings.forIntrests" to "utilities.deathSounds.enabled",
        "settings.forIntrestsVolume" to "utilities.deathSounds.settings.volume",
        "farming.highlightPests" to "farming.pests.highlightPests",
        "farming.pestHelper" to "farming.pests.pestHelper",
        "farming.pestSpawnCooldownWarning" to "farming.pests.pestSpawnCooldownWarning",
        "slayer.bossAlerts" to "slayer.alerts.bossAlerts",
        "slayer.minibossAlert" to "slayer.alerts.minibossAlert",
    )

    private val fieldIdentities = mapOf(
        "InventoryControlsConfig#inputMath" to "MiscFeatureConfig#inputMath",
        "InventoryTooltipsConfig#isTooltipBackgroundSolid" to "InventoryFeatureConfig#isTooltipBackgroundSolid",
        "StorageFeatureConfig#hideSacksMessages" to "ChatFeatureConfig\$MessageFilteringConfig#hideSacksMessages",
        "EnchantingFeatureConfig#isExperimentationTableHelperEnabled" to "InventoryFeatureConfig#isExperimentationTableHelperEnabled",
        "EventFeatureConfig#isMinisterInCalendarShown" to "InventoryFeatureConfig#isMinisterInCalendarShown",
        "PlayerHudConfig#isSkyBlockLevelBarEnabled" to "GuiFeatureConfig#isSkyBlockLevelBarEnabled",
        "VanillaUiConfig#crosshairVisibility" to "GuiFeatureConfig#crosshairVisibility",
        "VanillaUiConfig#areAbsorptionHeartsMerged" to "GuiFeatureConfig#areAbsorptionHeartsMerged",
        "VanillaUiConfig#isHeartBobbingDisabled" to "GuiFeatureConfig#isHeartBobbingDisabled",
        "WorldFeatureConfig#hideDeadEntities" to "MiscFeatureConfig#hideDeadEntities",
        "WorldFeatureConfig#keepSkyBlockResourcePack" to "MiscFeatureConfig#keepSkyBlockResourcePack",
        "UtilitiesFeatureConfig#shortWarpCommands" to "MiscFeatureConfig#shortWarpCommands",
        "DeathSoundsConfig#enabled" to "SettingsConfig#forIntrests",
        "DeathSoundsSettingsConfig#volume" to "SettingsConfig#forIntrestsVolume",
        "FarmingPestsConfig#highlightPests" to "FarmingFeatureConfig#highlightPests",
        "SlayerAlertsConfig#minibossAlert" to "SlayerFeatureConfig#minibossAlert",
    )

    fun apply(json: JsonObject) {
        json.moveFieldInto(json, "profitTracker", "profitTrackers")
        paths.forEach { (oldPath, newPath) ->
            val source = oldPath.substringBeforeLast('.', "").split('.')
                .filter(String::isNotEmpty)
                .fold(json as JsonObject?) { parent, name -> parent?.getObjectOrNull(name) }
            val field = oldPath.substringAfterLast('.')
            if (source?.has(field) != true) return@forEach
            val target = newPath.substringBeforeLast('.').split('.')
                .fold(json) { parent, name -> parent.getOrCreateObject(name) }
            source.moveFieldInto(target, field, newPath.substringAfterLast('.'))
        }
        json.getObjectOrNull("chat")?.remove("messageFiltering")
        json.remove("misc")
    }

    fun originalPath(path: String): String {
        val move = paths.entries.firstOrNull { (_, target) -> path == target || path.startsWith("$target.") }
            ?: return path
        return move.key + path.removePrefix(move.value)
    }

    fun originalFieldIdentity(field: Field): String {
        val identity = "${field.declaringClass.name}#${field.name}"
        val localIdentity = identity.removePrefix(CONFIG_PACKAGE)
        return fieldIdentities[localIdentity]?.let { CONFIG_PACKAGE + it } ?: identity
    }

    private const val CONFIG_PACKAGE = "com.skysoft.config."
}
