package com.skysoft.config

import com.google.gson.JsonObject

internal object SkysoftConfigMigrations {
    const val CURRENT_CONFIG_MIGRATION_VERSION = 28

    fun apply(json: JsonObject) {
        val migrationVersion = json.get(CONFIG_MIGRATION_VERSION_FIELD)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: 0
        if (migrationVersion < DIANA_FEATURE_ACCORDIONS_VERSION) migrateDianaFeatureAccordions(json)
        if (migrationVersion < DIANA_AND_TERRAIN_SETTINGS_VERSION) migrateDianaAndTerrainSettings(json)
        migrateOrganizedConfigLayout(json)
        if (migrationVersion < PRICE_TOOLTIP_CUSTOMIZATION_VERSION) migratePriceTooltipCustomization(json)
        if (migrationVersion < CONFIG_MENU_ORGANIZATION_VERSION) ConfigMenuOrganizationMigration.apply(json)
        if (migrationVersion < INVENTORY_EQUIPMENT_CATEGORY_VERSION) migrateInventoryEquipmentIntoCategory(json)
        if (migrationVersion < MODERN_STORAGE_OVERLAY_VERSION) migrateEnabledStorageOverlayToModern(json)
        if (migrationVersion < BETTER_SHURIKENS_CATEGORY_VERSION) migrateBetterShurikensIntoCategory(json)
        if (migrationVersion < VANILLA_UI_CATEGORY_VERSION) migrateVanillaUiCategory(json)
        if (migrationVersion < MENU_DROP_FIX_SAFETY_VERSION) {
            json.getObjectOrNull("fixes")
                ?.takeIf { it.has(SKYBLOCK_MENU_DROP_FIX_FIELD) }
                ?.addProperty(SKYBLOCK_MENU_DROP_FIX_FIELD, false)
        }
        if (migrationVersion < SELECTIVE_CUSTOM_BAR_ICONS_VERSION) migrateCustomBarIcons(json)
        if (migrationVersion < CUSTOM_BAR_DISPLAY_MODES_VERSION) migrateCustomBarDisplayModes(json)
        if (migrationVersion < MAX_ENCHANT_CHROMA_CATEGORY_VERSION) {
            json.getObjectOrNull("inventory")?.wrapFieldAsEnabled("maxEnchantChroma") { !it.isJsonObject }
        }
        if (migrationVersion < SPOTIFY_LYRICS_MODE_VERSION) migrateSpotifyLyricsMode(json)
        if (migrationVersion < SERVER_INFO_METRIC_COLORS_VERSION) migrateServerInfoMetricColors(json)
        if (migrationVersion < CURSOR_POSITION_PRESERVATION_CATEGORY_VERSION) migrateCursorPositionPreservation(json)
        if (migrationVersion < PARTICLE_QUALITY_SETUP_VERSION) migrateDianaParticleQualitySetup(json)
        if (migrationVersion < MOUSE_LOCK_FARMING_CATEGORY_VERSION) migrateMouseLockIntoFarming(json)
        if (migrationVersion < HONEYHIVE_HELPER_CATEGORY_VERSION) migrateHoneyhiveHelper(json)
        if (migrationVersion < STORAGE_OVERLAY_CATEGORY_VERSION) migrateStorageOverlayCategory(json)
        if (migrationVersion < SETTINGS_APPEARANCE_ACCORDIONS_VERSION) {
            json.getObjectOrNull("settings")?.moveFieldsInto(
                "configMenuAppearance",
                "searchHighlightColor",
                "selectedCategoryColor",
                "categoryColor",
                "subcategoryColor",
            )
        }
        if (migrationVersion < CATEGORY_LAYOUT_VERSION) ConfigCategoryLayoutMigration.apply(json)
        if (migrationVersion < KEYBIND_NUMBERING_VERSION) {
            json.addProperty(KEYBIND_NUMBERING_FIELD, KeybindNumbering.GLFW.name)
        }
        migrateItemListSortBar(json)
        json.addProperty(CONFIG_MIGRATION_VERSION_FIELD, CURRENT_CONFIG_MIGRATION_VERSION)
    }

    private fun migrateVanillaUiCategory(json: JsonObject) {
        val guiJson = json.getOrCreateObject("gui")
        val vanillaUiJson = guiJson.getOrCreateObject("vanillaUi")
        guiJson.moveFieldInto(vanillaUiJson, "areVanillaStatusEffectsHidden")
        json.getObjectOrNull("inventory")?.moveFieldInto(vanillaUiJson, "isVanillaRecipeBookHidden")
    }

    private fun migrateOrganizedConfigLayout(json: JsonObject) {
        json.getObjectOrNull("gui", "customBars", "settings")?.let { settingsJson ->
            CUSTOM_BAR_FIELDS.forEach { fieldName ->
                if (fieldName in CUSTOM_BAR_RESOURCE_FIELDS) {
                    settingsJson.copyFieldInto({ settingsJson.getOrCreateObject("bars") }, fieldName)
                }
                settingsJson.moveFieldInto({ settingsJson.getOrCreateObject("numbers") }, fieldName)
            }
        }
        json.getObjectOrNull("inventory", "tooltipScroll")
            ?.moveFieldsInto("settings", "interfaceScrollTooltipKey", "storageOverlayTooltipKey")
        json.getObjectOrNull("inventory", "inventoryEquipment")
            ?.moveFieldsInto("settings", "clickAction")
        json.getObjectOrNull("misc")?.wrapFieldAsEnabled("autoSprint") { it.isBooleanValue() }
    }

    private fun migrateInventoryEquipmentIntoCategory(json: JsonObject) {
        val inventoryJson = json.getObjectOrNull("inventory") ?: return
        val legacyEnabled = inventoryJson.get("isInventoryEquipmentEnabled") ?: return
        inventoryJson.getOrCreateObject("inventoryEquipment").add("enabled", legacyEnabled.deepCopy())
        inventoryJson.remove("isInventoryEquipmentEnabled")
    }

    private fun migrateEnabledStorageOverlayToModern(json: JsonObject) {
        val inventoryJson = json.getObjectOrNull("inventory") ?: return
        if (inventoryJson.get("isStorageOverlayEnabled").booleanOrNull() != true) return
        inventoryJson.getOrCreateObject("storageOverlay", "settings").addProperty("mode", StorageOverlayMode.MODERN.name)
    }

    private fun migrateBetterShurikensIntoCategory(json: JsonObject) {
        val combatJson = json.getObjectOrNull("combat") ?: return
        combatJson.moveFieldInto(
            { combatJson.getOrCreateObject("betterShurikens") },
            "isBetterShurikensEnabled",
            "enabled",
        )
    }

    private fun migrateMouseLockIntoFarming(json: JsonObject) {
        val miscJson = json.getObjectOrNull("misc") ?: return
        val mouseLockJson = miscJson.remove("mouseLock").objectOrNull() ?: return
        val farmingJson = json.getOrCreateObject("farming")
        mouseLockJson.getObjectOrNull("settings")?.moveFieldInto(
            { farmingJson.getOrCreateObject("pestHelper", "settings") },
            "unlockOnPestSpawn",
            "unlockOnWarp",
        )
        if (!farmingJson.has("mouseLock")) farmingJson.add("mouseLock", mouseLockJson)
    }

    private val CUSTOM_BAR_FIELDS = CUSTOM_BAR_RESOURCE_FIELDS + setOf("defense", "speed", "air")
    private const val CONFIG_MIGRATION_VERSION_FIELD = "configMigrationVersion"
    private const val MENU_DROP_FIX_SAFETY_VERSION = 1
    private const val CONFIG_MENU_ORGANIZATION_VERSION = 3
    private const val INVENTORY_EQUIPMENT_CATEGORY_VERSION = 4
    private const val PRICE_TOOLTIP_CUSTOMIZATION_VERSION = 5
    private const val MODERN_STORAGE_OVERLAY_VERSION = 6
    private const val BETTER_SHURIKENS_CATEGORY_VERSION = 7
    private const val VANILLA_UI_CATEGORY_VERSION = 8
    private const val SELECTIVE_CUSTOM_BAR_ICONS_VERSION = 9
    private const val CUSTOM_BAR_DISPLAY_MODES_VERSION = 10
    private const val MAX_ENCHANT_CHROMA_CATEGORY_VERSION = 11
    private const val SPOTIFY_LYRICS_MODE_VERSION = 12
    private const val SERVER_INFO_METRIC_COLORS_VERSION = 13
    private const val CURSOR_POSITION_PRESERVATION_CATEGORY_VERSION = 14
    private const val DIANA_FEATURE_ACCORDIONS_VERSION = 15
    private const val DIANA_AND_TERRAIN_SETTINGS_VERSION = 19
    private const val PARTICLE_QUALITY_SETUP_VERSION = 20
    private const val MOUSE_LOCK_FARMING_CATEGORY_VERSION = 21
    private const val HONEYHIVE_HELPER_CATEGORY_VERSION = 23
    private const val STORAGE_OVERLAY_CATEGORY_VERSION = 24
    private const val SETTINGS_APPEARANCE_ACCORDIONS_VERSION = 25
    private const val CATEGORY_LAYOUT_VERSION = 26
    private const val KEYBIND_NUMBERING_VERSION = 27
    private const val KEYBIND_NUMBERING_FIELD = "keybindNumbering"
    private const val SKYBLOCK_MENU_DROP_FIX_FIELD = "preventSkyBlockMenuOpeningOnInventoryDrop"
}

private fun migrateItemListSortBar(json: JsonObject) {
    val itemList = json.getObjectOrNull("items", "itemList") ?: return
    val sortBar = itemList.getOrCreateObject("sortBar")
    if (!sortBar.has("enabled")) sortBar.addProperty("enabled", true)
    val sort = sortBar.getOrCreateObject("sort")
    listOf("name", "rarity", "worth").forEach { key ->
        if (!sort.has(key)) sort.addProperty(key, true)
    }
    val filters = sortBar.getOrCreateObject("filters")
    listOf("mobs", "pets", "tools", "armor", "accessories").forEach { key ->
        if (!filters.has(key)) filters.addProperty(key, true)
    }
}

private fun migrateDianaFeatureAccordions(json: JsonObject) {
    val dianaJson = json.getObjectOrNull("events", "diana") ?: return
    val globallyEnabled = dianaJson.remove("enabled").booleanOrNull() ?: false
    val settingsJson = dianaJson.remove("settings").objectOrNull() ?: JsonObject()
    val detailsJson = dianaJson.remove("details").objectOrNull() ?: JsonObject()

    fun feature(name: String, enabled: Boolean): JsonObject =
        dianaJson.getOrCreateObject(name).also { featureJson ->
            if (!featureJson.has("enabled")) featureJson.addProperty("enabled", enabled)
        }

    fun isEnabledBy(legacyFieldName: String): Boolean =
        globallyEnabled && (settingsJson.remove(legacyFieldName).booleanOrNull() ?: true)

    val burrowHelper = feature("burrowHelper", globallyEnabled)
    settingsJson.moveFieldsInto(burrowHelper.getOrCreateObject("settings"), DIANA_BURROW_SETTINGS_FIELDS)
    detailsJson.moveFieldsInto(burrowHelper.getOrCreateObject("details"), DIANA_BURROW_DETAILS_FIELDS)

    val shareMobs = settingsJson.remove("rareMobSharing").booleanOrNull() ?: true
    val rareMobSharing = feature("rareMobSharing", globallyEnabled)
    val rareMobSettings = rareMobSharing.getOrCreateObject("settings")
    if (!rareMobSettings.has("shareMobs")) rareMobSettings.addProperty("shareMobs", shareMobs)
    settingsJson.moveFieldsInto(rareMobSettings, DIANA_RARE_MOB_SETTINGS_FIELDS)
    detailsJson.moveFieldsInto(rareMobSharing.getOrCreateObject("details"), DIANA_RARE_MOB_DETAILS_FIELDS)

    val lobbyCompromised = feature("lobbyCompromised", isEnabledBy("lobbyCompromised")).getOrCreateObject("settings")
    settingsJson.moveFieldInto(lobbyCompromised, "lobbyCompromisedStrangerLimit", "strangerLimit")
    settingsJson.moveFieldInto(lobbyCompromised, "lobbyCompromisedAlerts", "alerts")

    feature("sphinxHelper", isEnabledBy("sphinxAnswers"))

    val quickWarps = feature("quickWarps", isEnabledBy("warpHint")).getOrCreateObject("settings")
    settingsJson.moveFieldsInto(quickWarps, DIANA_QUICK_WARP_SETTINGS_FIELDS)

    val keepTerrainLoaded = isEnabledBy("keepHubTerrainLoaded")
    val miscJson = json.getOrCreateObject("misc")
    if (!miscJson.has("keepTerrainLoaded")) miscJson.addProperty("keepTerrainLoaded", keepTerrainLoaded)
}

private fun migrateDianaAndTerrainSettings(json: JsonObject) {
    json.getObjectOrNull("misc")?.let { miscJson ->
        miscJson.wrapFieldAsEnabled("keepTerrainLoaded") { it.isBooleanValue() }
        val islands = miscJson.getObjectOrNull("keepTerrainLoaded", "settings")?.getAsJsonArray("islands")
        if (islands != null) {
            for (index in islands.size() - 1 downTo 0) {
                if (islands[index].asString in NON_PERSISTENT_TERRAIN_ISLANDS) islands.remove(index)
            }
        }
    }

    val diana = json.getObjectOrNull("events", "diana") ?: return
    val rareMobSharing = diana.getObjectOrNull("rareMobSharing") ?: return
    val lootshare = diana.getObjectOrNull("lootshare")
        ?: rareMobSharing.remove("lootshare").objectOrNull()
        ?: JsonObject()
    diana.add("lootshare", lootshare)
    if (!lootshare.has("enabled")) {
        val rareMobSharingEnabled = rareMobSharing.get("enabled").booleanOrNull() ?: false
        lootshare.addProperty("enabled", rareMobSharingEnabled)
    }
    val settings = lootshare.getOrCreateObject("settings")
    if (!settings.has("shareSecuredMessage")) settings.addProperty("shareSecuredMessage", true)
    val details = lootshare.getOrCreateObject("details")
    details.moveFieldInto(settings, "partyCheckmarks")
    if (!settings.has("partyCheckmarks")) settings.addProperty("partyCheckmarks", true)
    rareMobSharing.getObjectOrNull("settings")?.moveFieldInto(details, "lootshareRadius")
    rareMobSharing.getObjectOrNull("details")?.let { legacyDetails ->
        legacyDetails.moveFieldsInto(details, DIANA_RARE_MOB_DETAILS_FIELDS)
        if (legacyDetails.size() == 0) rareMobSharing.remove("details")
    }

    val partyCommands = diana.getOrCreateObject("partyCommands")
    if (!partyCommands.has("enabled")) {
        val wasEnabled = listOf("burrowHelper", "rareMobSharing", "lobbyCompromised", "sphinxHelper", "quickWarps")
            .any { feature -> diana.getObjectOrNull(feature)?.get("enabled").booleanOrNull() == true }
        partyCommands.addProperty("enabled", wasEnabled)
    }
}

private val NON_PERSISTENT_TERRAIN_ISLANDS = setOf(
    "PRIVATE_ISLANDS",
    "KUUDRA",
    "DUNGEONS",
    "GLACITE_MINESHAFTS",
)
private val DIANA_BURROW_SETTINGS_FIELDS = listOf("crosshairLine", "clickCounter", "clickCounterPosition")
private val DIANA_BURROW_DETAILS_FIELDS = listOf(
    "boldText",
    "hideGuessArrows",
    "burrowBoxColorMode",
    "burrowBoxColor",
    "labelFormat",
    "startTextColor",
    "mobTextColor",
    "treasureTextColor",
    "guessTextColor",
)
private val DIANA_RARE_MOB_SETTINGS_FIELDS = listOf("sharedRareMobs", "receivedRareMobs", "lootshareRadius")
private val DIANA_RARE_MOB_DETAILS_FIELDS = listOf("lootshareMissingColor", "lootshareReadyColor")
private val DIANA_QUICK_WARP_SETTINGS_FIELDS = listOf("warpKey", "minWarpSavings")

private fun migrateCursorPositionPreservation(json: JsonObject) {
    val inventoryJson = json.getObjectOrNull("inventory") ?: return
    inventoryJson.moveFieldInto(
        { inventoryJson.getOrCreateObject("cursorPositionPreservation") },
        "preserveCursorPosition",
        "enabled",
    )
}

private fun migrateDianaParticleQualitySetup(json: JsonObject) {
    val diana = json.getObjectOrNull("events", "diana") ?: return
    if (diana.getObjectOrNull("burrowHelper")?.get("enabled").booleanOrNull() != true) return
    diana.getOrCreateObject("particleQuality").addProperty(
        "automaticMigrationAttemptsRemaining",
        DianaParticleQualityConfig.MAX_AUTOMATIC_MIGRATION_ATTEMPTS,
    )
}

private val CUSTOM_BAR_RESOURCE_FIELDS = setOf("health", "mana", "vitality", "experience")

private fun migrateSpotifyLyricsMode(json: JsonObject) {
    val details = json.getObjectOrNull("gui", "spotifyDisplay", "details") ?: return
    val enabled = details.remove("syncedLyrics").booleanOrNull() ?: return
    if (!details.has("lyricsMode")) {
        details.addProperty("lyricsMode", if (enabled) SpotifyLyricsMode.FADE.name else SpotifyLyricsMode.OFF.name)
    }
}

private fun migrateServerInfoMetricColors(json: JsonObject) {
    val details = json.getObjectOrNull("gui", "serverInfoDisplay", "details") ?: return
    val color = details.remove("color") ?: return
    listOf("fpsColor", "tpsColor", "pingColor").forEach { fieldName ->
        if (!details.has(fieldName)) details.add(fieldName, color.deepCopy())
    }
}

private fun migrateCustomBarIcons(json: JsonObject) {
    val details = json.getObjectOrNull("gui", "customBars", "details") ?: return
    val icons = details.get("icons")?.takeIf { it.isJsonPrimitive }?.asString ?: return
    if (icons != "NONE") return
    details.addProperty("icons", CustomBarIconPosition.LEFT.name)
    CUSTOM_BAR_RESOURCE_FIELDS.forEach { fieldName ->
        val elementDetails = details.getOrCreateObject(fieldName)
        if (!elementDetails.has("showIcon")) elementDetails.addProperty("showIcon", false)
    }
}

private fun migrateCustomBarDisplayModes(json: JsonObject) {
    val settings = json.getObjectOrNull("gui", "customBars", "settings") ?: return
    val displays = settings.getOrCreateObject("displays")
    val bars = settings.getObjectOrNull("bars")
    CUSTOM_BAR_RESOURCE_FIELDS.forEach { fieldName ->
        bars?.moveBooleanToDisplayMode(displays, fieldName)
    }
    settings.remove("bars")

    val numbers = settings.getOrCreateObject("numbers")
    listOf("defense", "air").forEach { fieldName ->
        numbers.moveBooleanToDisplayMode(displays, fieldName)
    }
    numbers.moveFieldInto(displays, "speed")
}

private fun JsonObject.moveBooleanToDisplayMode(target: JsonObject, fieldName: String) {
    val enabled = get(fieldName).booleanOrNull() ?: return
    if (!target.has(fieldName)) {
        val mode = if (enabled) CustomBarDisplayMode.CUSTOM else CustomBarDisplayMode.VANILLA
        target.addProperty(fieldName, mode.name)
    }
    remove(fieldName)
}

private fun migrateHoneyhiveHelper(json: JsonObject) {
    val foraging = json.getObjectOrNull("foraging") ?: return
    foraging.wrapFieldAsEnabled("honeyhiveHelper") { it.isJsonPrimitive }
    val settings = foraging.getObjectOrNull("honeyhiveHelper", "settings") ?: return
    mapOf("showDisplay" to "display", "maxRows" to "maximumLines", "showOutsideIsland" to "showOutsideTorrhus")
        .forEach { (oldName, newName) -> settings.moveFieldInto(settings, oldName, newName) }
}

private fun migrateStorageOverlayCategory(json: JsonObject) {
    val inventory = json.getObjectOrNull("inventory") ?: return
    val overlay = inventory.getOrCreateObject("storageOverlay")
    inventory.moveFieldInto(overlay, "isStorageOverlayEnabled", "enabled")
    val settings = overlay.getOrCreateObject("settings")
    val details = overlay.getOrCreateObject("details")
    settings.moveFieldInto(details, "theme")
    details.moveFieldInto(settings, "scrollSpeed")
}

private fun migratePriceTooltipCustomization(json: JsonObject) {
    val settingsJson = json.getObjectOrNull("inventory", "priceTooltips", "settings") ?: return
    val legacyType = settingsJson.remove("bazaarPriceType") ?: return
    if (!settingsJson.has("priceLines")) {
        settingsJson.add("legacyBazaarPriceType", legacyType)
    }
}
