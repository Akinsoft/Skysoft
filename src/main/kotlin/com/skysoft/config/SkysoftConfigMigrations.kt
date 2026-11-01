package com.skysoft.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import java.util.Locale

internal object SkysoftConfigMigrations {
    const val CURRENT_CONFIG_MIGRATION_VERSION = 27

    fun apply(json: JsonObject, gson: Gson) {
        val migrationVersion = json.get(CONFIG_MIGRATION_VERSION_FIELD)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: 0
        importLegacyStorage(json, gson)
        migrateBazaarIntoInventory(json)
        migrateActionBarBackgroundIntoGui(json)
        if (migrationVersion < DIANA_FEATURE_ACCORDIONS_VERSION) {
            migrateDianaSettings(json)
            migrateRareLootSharingIntoMisc(json)
            migrateBuggedNameplatesIntoMisc(json)
            migrateDianaFeatureAccordions(json)
        }
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
        json.addProperty(CONFIG_MIGRATION_VERSION_FIELD, CURRENT_CONFIG_MIGRATION_VERSION)
    }

    private fun migrateVanillaUiCategory(json: JsonObject) {
        val guiJson = json.getOrCreateObject("gui")
        val vanillaUiJson = guiJson.getOrCreateObject("vanillaUi")
        guiJson.moveFieldInto(vanillaUiJson, "areVanillaStatusEffectsHidden")
        json.getObjectOrNull("inventory")?.moveFieldInto(vanillaUiJson, "isVanillaRecipeBookHidden")
    }

    private fun importLegacyStorage(json: JsonObject, gson: Gson) {
        json.getAsJsonObject("storage")?.let { legacyStorageJson ->
            val legacyStorage = gson.fromJson(legacyStorageJson, ProfileStorage::class.java)
            if (legacyStorage != null) ProfileStorageApi.importLegacyStorage(legacyStorage)
        }
    }

    private fun migrateBazaarIntoInventory(json: JsonObject) {
        val legacyJson = json.getObjectOrNull("bazaar") ?: return
        val inventoryJson = json.getOrCreateObject("inventory")
        if (!inventoryJson.has("bazaar")) inventoryJson.add("bazaar", legacyJson.deepCopy())
        json.remove("bazaar")
    }

    private fun migrateActionBarBackgroundIntoGui(json: JsonObject) {
        json.getObjectOrNull("misc")
            ?.moveFieldInto({ json.getOrCreateObject("gui", "actionBar") }, "actionBarBackground", "background")
    }

    private fun migrateDianaSettings(json: JsonObject) {
        val dianaJson = json.getObjectOrNull("events", "diana") ?: return
        val settingsJson = dianaJson.getOrCreateObject("settings")
        dianaJson.remove("waypoints")
        settingsJson.remove("waypoints")
        dianaJson.moveFieldsInto(settingsJson, DIANA_SETTINGS_FIELDS)
    }

    private fun migrateBuggedNameplatesIntoMisc(json: JsonObject) {
        json.getObjectOrNull("events", "diana", "details")
            ?.moveFieldInto({ json.getOrCreateObject("misc") }, "hideBuggedNameplates")
    }

    private fun migrateRareLootSharingIntoMisc(json: JsonObject) {
        json.getObjectOrNull("events", "diana", "settings")
            ?.moveFieldsInto(json.getOrCreateObject("misc"), RARE_LOOT_FIELDS)
    }

    private fun migrateOrganizedConfigLayout(json: JsonObject) {
        migrateGuiLayout(json)
        migrateInventoryLayout(json)
        migrateChatLayout(json)
        migrateHuntingLayout(json)
        migrateFishingLayout(json)
        migrateMiscLayout(json)
    }

    private fun migrateGuiLayout(json: JsonObject) {
        val guiJson = json.getObjectOrNull("gui") ?: return
        guiJson.moveFieldInto({ guiJson.getOrCreateObject("positionEditor") }, "positionEditorKeybind", "keybind")
        guiJson.getObjectOrNull("inventoryScreen")
            ?.moveFieldsInto("settings", "inventoryGuiScale", "tooltipGuiScale")
        guiJson.getObjectOrNull("heldItem")?.migrateHeldItemTextureModes()
        guiJson.getObjectOrNull("customBars", "settings")?.let { settingsJson ->
            CUSTOM_BAR_FIELDS.forEach { fieldName ->
                if (fieldName in CUSTOM_BAR_RESOURCE_FIELDS) {
                    settingsJson.copyFieldInto({ settingsJson.getOrCreateObject("bars") }, fieldName)
                }
                settingsJson.moveFieldInto({ settingsJson.getOrCreateObject("numbers") }, fieldName)
            }
        }
    }

    private fun migrateInventoryLayout(json: JsonObject) {
        val inventoryJson = json.getObjectOrNull("inventory") ?: return
        inventoryJson.moveFieldInto({ json.getOrCreateObject("misc", "fixes") }, SKYBLOCK_MENU_DROP_FIX_FIELD)

        inventoryJson.getObjectOrNull("bazaar")?.let { bazaarJson ->
            bazaarJson.getObjectOrNull("tracker")?.let { trackerJson ->
                trackerJson.moveFieldsInto(bazaarJson, BAZAAR_TRACKER_FIELDS)
                bazaarJson.remove("tracker")
            }
            bazaarJson.moveFieldInto(bazaarJson, "trackerEnabled", "enabled")
            bazaarJson.moveFieldsIntoSettingsAndDetails(BAZAAR_SETTINGS_FIELDS, BAZAAR_DETAILS_FIELDS)
        }

        inventoryJson.getObjectOrNull("tooltipScroll")
            ?.moveFieldsIntoSettingsAndDetails(TOOLTIP_SCROLL_SETTINGS_FIELDS, TOOLTIP_SCROLL_DETAILS_FIELDS)
        inventoryJson.getObjectOrNull("priceTooltips")
            ?.moveFieldsInto("settings", PRICE_TOOLTIP_SETTINGS_FIELDS)
        inventoryJson.getObjectOrNull("storageOverlay")
            ?.moveFieldsIntoSettingsAndDetails(STORAGE_OVERLAY_SETTINGS_FIELDS, STORAGE_OVERLAY_DETAILS_FIELDS)
        inventoryJson.getObjectOrNull("inventoryButtons")
            ?.moveFieldsIntoSettingsAndDetails(INVENTORY_BUTTON_SETTINGS_FIELDS, INVENTORY_BUTTON_DETAILS_FIELDS)
        inventoryJson.getObjectOrNull("inventoryEquipment")
            ?.moveFieldsInto("settings", INVENTORY_EQUIPMENT_SETTINGS_FIELDS)
        inventoryJson.getObjectOrNull("fullInventory")
            ?.moveFieldsIntoSettingsAndDetails(FULL_INVENTORY_SETTINGS_FIELDS, FULL_INVENTORY_DETAILS_FIELDS)
        inventoryJson.getObjectOrNull("slotBindings")
            ?.moveFieldsIntoSettingsAndDetails(SLOT_BINDING_SETTINGS_FIELDS, SLOT_BINDING_DETAILS_FIELDS)
        inventoryJson.getObjectOrNull("smoothSwapping")
            ?.moveFieldsIntoSettingsAndDetails(SMOOTH_SWAPPING_SETTINGS_FIELDS, SMOOTH_SWAPPING_DETAILS_FIELDS)
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

    private fun migrateChatLayout(json: JsonObject) {
        json.getObjectOrNull("chat", "smoothChat")
            ?.moveFieldsIntoSettingsAndDetails(SMOOTH_CHAT_SETTINGS_FIELDS, SMOOTH_CHAT_DETAILS_FIELDS)
    }

    private fun migrateHuntingLayout(json: JsonObject) {
        json.getObjectOrNull("hunting", "lotumHelper")
            ?.moveFieldsInto("settings", LOTUM_HELPER_SETTINGS_FIELDS)
    }

    private fun migrateFishingLayout(json: JsonObject) {
        json.getObjectOrNull("fishing", "hotspotSharing")
            ?.moveFieldsIntoSettingsAndDetails(HOTSPOT_SHARING_SETTINGS_FIELDS, HOTSPOT_SHARING_DETAILS_FIELDS)
    }

    private fun migrateMiscLayout(json: JsonObject) {
        val miscJson = json.getObjectOrNull("misc") ?: return
        miscJson.wrapFieldAsEnabled("autoSprint") { it.isBooleanValue() }
        miscJson.wrapFieldAsEnabled("rareLootSharing") { !it.isJsonObject }
        val rareLootJson = miscJson.getOrCreateObject("rareLootSharing")
        miscJson.moveFieldInto({ rareLootJson.getOrCreateObject("settings") }, "rareLootValue")
        miscJson.getObjectOrNull("blockOverlay")
            ?.moveFieldsInto("settings", BLOCK_OVERLAY_SETTINGS_FIELDS)
        val legacyFixesJson = miscJson.getOrCreateObject("fixes")
        miscJson.moveFieldsInto(legacyFixesJson, MISC_FIX_FIELDS)
        legacyFixesJson.moveFieldsInto(json.getOrCreateObject("fixes"), legacyFixesJson.keySet().toList())
        miscJson.remove("fixes")
    }

    private val DIANA_SETTINGS_FIELDS = listOf(
        "crosshairLine",
        "clickCounter",
        "clickCounterPosition",
        "warpHint",
        "warpKey",
        "minWarpSavings",
    )
    private val RARE_LOOT_FIELDS = listOf("rareLootSharing", "rareLootValue")
    private val BAZAAR_TRACKER_FIELDS = listOf(
        "enabled",
        "maxOrders",
        "showBackground",
        "flippingInfo",
        "visualIndicators",
        "estimateFills",
        "sounds",
        "position",
    )
    private val BAZAAR_SETTINGS_FIELDS = listOf("maxOrders", "estimateFills", "sounds")
    private val BAZAAR_DETAILS_FIELDS = listOf("showBackground", "flippingInfo", "visualIndicators")
    private val TOOLTIP_SCROLL_SETTINGS_FIELDS = listOf(
        "enableScrollWheel",
        "interfaceScrollTooltipKey",
        "storageOverlayTooltipKey",
        "enableWASD",
        "mouseScrollingSpeed",
        "keyboardScrollingSpeed",
        "moveUpKey",
        "moveDownKey",
        "horizontalMovementKey",
        "resetTooltipKey",
    )
    private val TOOLTIP_SCROLL_DETAILS_FIELDS = listOf(
        "startOnTop",
        "resetPositionWhenNotHovered",
        "useLeftShift",
        "invertHorizontalMovement",
        "invertVerticalMovement",
        "scrollSmoothness",
    )
    private val PRICE_TOOLTIP_SETTINGS_FIELDS = listOf("requireKey", "hotkey", "bazaarPriceType")
    private val STORAGE_OVERLAY_SETTINGS_FIELDS = listOf("miniMenu")
    private val STORAGE_OVERLAY_DETAILS_FIELDS = listOf("columns", "height", "scrollSpeed")
    private val INVENTORY_BUTTON_SETTINGS_FIELDS = listOf("clickType")
    private val INVENTORY_BUTTON_DETAILS_FIELDS = listOf("tooltipDelay")
    private val INVENTORY_EQUIPMENT_SETTINGS_FIELDS = listOf("clickAction")
    private val FULL_INVENTORY_SETTINGS_FIELDS = listOf("emptySlots")
    private val FULL_INVENTORY_DETAILS_FIELDS = listOf("playSound")
    private val SLOT_BINDING_SETTINGS_FIELDS = listOf("bindingKey")
    private val SLOT_BINDING_DETAILS_FIELDS = listOf(
        "showHighlights",
        "highlightColor",
        "highlightStyle",
        "showShiftHoverHighlight",
    )
    private val SMOOTH_SWAPPING_SETTINGS_FIELDS = listOf("animationSpeed")
    private val SMOOTH_SWAPPING_DETAILS_FIELDS = listOf("animationCurve")
    private val SMOOTH_CHAT_SETTINGS_FIELDS = listOf("messageAnimationDuration", "chatOpenAnimationDuration")
    private val SMOOTH_CHAT_DETAILS_FIELDS = listOf("hideMessageIndicator")
    private val LOTUM_HELPER_SETTINGS_FIELDS = listOf("highlightLotums")
    private val HOTSPOT_SHARING_SETTINGS_FIELDS = listOf("showSharedWaypoints")
    private val HOTSPOT_SHARING_DETAILS_FIELDS = listOf("crosshairLine")
    private val BLOCK_OVERLAY_SETTINGS_FIELDS = listOf("color", "combinations")
    private val MISC_FIX_FIELDS = listOf("hideGlitchMobs", "hideBuggedNameplates", "playerHeadSkinFix")
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

private fun JsonObject.migrateHeldItemTextureModes() {
    val legacyItemIds = get("vanillaTextureItemIds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val textureModes = getOrCreateObject("itemTextureModes")
    val configuredItemIds = textureModes.keySet().mapTo(mutableSetOf()) { it.trim().uppercase(Locale.US) }
    legacyItemIds.forEach { itemIdJson ->
        val itemId = itemIdJson.asString.trim().uppercase(Locale.US)
        if (itemId.isNotEmpty() && configuredItemIds.add(itemId)) {
            textureModes.addProperty(itemId, HeldItemTextureMode.VANILLA.name)
        }
    }
    remove("vanillaTextureItemIds")
}
