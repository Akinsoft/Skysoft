package com.skysoft.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import java.util.Locale

internal object SkysoftConfigMigrations {
    const val CURRENT_CONFIG_MIGRATION_VERSION = 20

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
        if (migrationVersion < PRICE_TOOLTIP_CUSTOMIZATION_VERSION) {
            migratePriceTooltipCustomization(json)
        }
        if (migrationVersion < CONFIG_MENU_ORGANIZATION_VERSION) {
            ConfigMenuOrganizationMigration.apply(json)
        }
        if (migrationVersion < INVENTORY_EQUIPMENT_CATEGORY_VERSION) {
            migrateInventoryEquipmentIntoCategory(json)
        }
        if (migrationVersion < MODERN_STORAGE_OVERLAY_VERSION) {
            migrateEnabledStorageOverlayToModern(json)
        }
        if (migrationVersion < BETTER_SHURIKENS_CATEGORY_VERSION) {
            migrateBetterShurikensIntoCategory(json)
        }
        if (migrationVersion < VANILLA_UI_CATEGORY_VERSION) {
            val guiJson = json.getOrCreateObject("gui")
            val vanillaUiJson = guiJson.getOrCreateObject("vanillaUi")
            guiJson.remove("areVanillaStatusEffectsHidden")?.let { legacyValue ->
                if (!vanillaUiJson.has("areVanillaStatusEffectsHidden")) {
                    vanillaUiJson.add("areVanillaStatusEffectsHidden", legacyValue.deepCopy())
                }
            }
            json.getObjectOrNull("inventory")?.remove("isVanillaRecipeBookHidden")?.let { legacyValue ->
                if (!vanillaUiJson.has("isVanillaRecipeBookHidden")) {
                    vanillaUiJson.add("isVanillaRecipeBookHidden", legacyValue.deepCopy())
                }
            }
        }
        if (migrationVersion < MENU_DROP_FIX_SAFETY_VERSION) {
            json.getObjectOrNull("fixes")
                ?.takeIf { it.has(SKYBLOCK_MENU_DROP_FIX_FIELD) }
                ?.addProperty(SKYBLOCK_MENU_DROP_FIX_FIELD, false)
        }
        if (migrationVersion < SELECTIVE_CUSTOM_BAR_ICONS_VERSION) migrateCustomBarIcons(json)
        if (migrationVersion < CUSTOM_BAR_DISPLAY_MODES_VERSION) migrateCustomBarDisplayModes(json)
        if (migrationVersion < MAX_ENCHANT_CHROMA_CATEGORY_VERSION) {
            json.getObjectOrNull("inventory")?.let { inventoryJson ->
                val legacyEnabled = inventoryJson.get("maxEnchantChroma")?.takeUnless { it.isJsonObject }
                if (legacyEnabled != null) {
                    inventoryJson.add(
                        "maxEnchantChroma",
                        JsonObject().also { it.add("enabled", legacyEnabled.deepCopy()) },
                    )
                }
            }
        }
        if (migrationVersion < SPOTIFY_LYRICS_MODE_VERSION) migrateSpotifyLyricsMode(json)
        if (migrationVersion < SERVER_INFO_METRIC_COLORS_VERSION) migrateServerInfoMetricColors(json)
        migrateCursorPositionPreservation(json, migrationVersion)
        migrateDianaParticleQualitySetup(json, migrationVersion)
        json.addProperty(CONFIG_MIGRATION_VERSION_FIELD, CURRENT_CONFIG_MIGRATION_VERSION)
    }

    private fun importLegacyStorage(json: JsonObject, gson: Gson) {
        json.getAsJsonObject("storage")?.let { legacyStorageJson ->
            val legacyStorage = gson.fromJson(legacyStorageJson, ProfileStorage::class.java)
            if (legacyStorage != null) ProfileStorageApi.importLegacyStorage(legacyStorage)
        }
    }

    private fun migrateBazaarIntoInventory(json: JsonObject) {
        migrateObjectIntoSection(json, legacyName = "bazaar", sectionName = "inventory", targetName = "bazaar")
    }

    private fun migrateActionBarBackgroundIntoGui(json: JsonObject) {
        val miscJson = json.getObjectOrNull("misc") ?: return
        val legacyBackground = miscJson.get("actionBarBackground") ?: return
        val guiJson = json.getOrCreateObject("gui")
        val actionBarJson = guiJson.getOrCreateObject("actionBar")
        if (!actionBarJson.has("background")) {
            actionBarJson.add("background", legacyBackground.deepCopy())
        }
        miscJson.remove("actionBarBackground")
    }

    private fun migrateDianaSettings(json: JsonObject) {
        val dianaJson = json.getObjectOrNull("events")?.getObjectOrNull("diana") ?: return
        val settingsJson = dianaJson.getOrCreateObject("settings")
        dianaJson.remove("waypoints")
        settingsJson.remove("waypoints")
        DIANA_SETTINGS_FIELDS.forEach { fieldName ->
            val legacyValue = dianaJson.get(fieldName) ?: return@forEach
            if (!settingsJson.has(fieldName)) {
                settingsJson.add(fieldName, legacyValue.deepCopy())
            }
            dianaJson.remove(fieldName)
        }
    }

    private fun migrateBuggedNameplatesIntoMisc(json: JsonObject) {
        val dianaDetailsJson = json.getObjectOrNull("events")
            ?.getObjectOrNull("diana")
            ?.getObjectOrNull("details")
            ?: return
        val legacyValue = dianaDetailsJson.get("hideBuggedNameplates") ?: return
        val miscJson = json.getOrCreateObject("misc")
        if (!miscJson.has("hideBuggedNameplates")) {
            miscJson.add("hideBuggedNameplates", legacyValue.deepCopy())
        }
        dianaDetailsJson.remove("hideBuggedNameplates")
    }

    private fun migrateRareLootSharingIntoMisc(json: JsonObject) {
        val dianaSettingsJson = json.getObjectOrNull("events")
            ?.getObjectOrNull("diana")
            ?.getObjectOrNull("settings")
            ?: return
        val miscJson = json.getOrCreateObject("misc")
        RARE_LOOT_FIELDS.forEach { fieldName ->
            val legacyValue = dianaSettingsJson.get(fieldName) ?: return@forEach
            if (!miscJson.has(fieldName)) {
                miscJson.add(fieldName, legacyValue.deepCopy())
            }
            dianaSettingsJson.remove(fieldName)
        }
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
        guiJson.get("positionEditorKeybind")?.let { legacyKeybind ->
            val positionEditorJson = guiJson.getOrCreateObject("positionEditor")
            if (!positionEditorJson.has("keybind")) positionEditorJson.add("keybind", legacyKeybind.deepCopy())
            guiJson.remove("positionEditorKeybind")
        }
        guiJson.getObjectOrNull("inventoryScreen")
            ?.moveFieldsInto("settings", listOf("inventoryGuiScale", "tooltipGuiScale"))
        guiJson.getObjectOrNull("heldItem")?.migrateHeldItemTextureModes()
        guiJson.getObjectOrNull("customBars")?.getObjectOrNull("settings")?.let { settingsJson ->
            CUSTOM_BAR_FIELDS.forEach { fieldName ->
                val legacyValue = settingsJson.remove(fieldName) ?: return@forEach
                if (fieldName in CUSTOM_BAR_RESOURCE_FIELDS) {
                    val barsJson = settingsJson.getOrCreateObject("bars")
                    if (!barsJson.has(fieldName)) barsJson.add(fieldName, legacyValue.deepCopy())
                }
                val numbersJson = settingsJson.getOrCreateObject("numbers")
                if (!numbersJson.has(fieldName)) numbersJson.add(fieldName, legacyValue.deepCopy())
            }
        }
    }

    private fun migrateInventoryLayout(json: JsonObject) {
        val inventoryJson = json.getObjectOrNull("inventory") ?: return
        inventoryJson.get(SKYBLOCK_MENU_DROP_FIX_FIELD)?.let { legacyValue ->
            val fixesJson = json.getOrCreateObject("misc").getOrCreateObject("fixes")
            if (!fixesJson.has(SKYBLOCK_MENU_DROP_FIX_FIELD)) {
                fixesJson.add(SKYBLOCK_MENU_DROP_FIX_FIELD, legacyValue.deepCopy())
            }
            inventoryJson.remove(SKYBLOCK_MENU_DROP_FIX_FIELD)
        }

        inventoryJson.getObjectOrNull("bazaar")?.let { bazaarJson ->
            val trackerJson = bazaarJson.getObjectOrNull("tracker")
            if (trackerJson != null) {
                BAZAAR_TRACKER_FIELDS.forEach { fieldName ->
                    trackerJson.copyFieldInto(bazaarJson, fieldName)
                }
                bazaarJson.remove("tracker")
            }
            bazaarJson.get("trackerEnabled")?.let { legacyEnabled ->
                if (!bazaarJson.has("enabled")) bazaarJson.add("enabled", legacyEnabled.deepCopy())
                bazaarJson.remove("trackerEnabled")
            }
            bazaarJson.moveFieldsInto("settings", BAZAAR_SETTINGS_FIELDS)
            bazaarJson.moveFieldsInto("details", BAZAAR_DETAILS_FIELDS)
        }

        inventoryJson.getObjectOrNull("tooltipScroll")?.let { tooltipScrollJson ->
            tooltipScrollJson.moveFieldsInto("settings", TOOLTIP_SCROLL_SETTINGS_FIELDS)
            tooltipScrollJson.moveFieldsInto("details", TOOLTIP_SCROLL_DETAILS_FIELDS)
        }
        inventoryJson.getObjectOrNull("priceTooltips")
            ?.moveFieldsInto("settings", PRICE_TOOLTIP_SETTINGS_FIELDS)
        inventoryJson.getObjectOrNull("storageOverlay")?.let { storageOverlayJson ->
            storageOverlayJson.moveFieldsInto("settings", STORAGE_OVERLAY_SETTINGS_FIELDS)
            storageOverlayJson.moveFieldsInto("details", STORAGE_OVERLAY_DETAILS_FIELDS)
        }
        inventoryJson.getObjectOrNull("inventoryButtons")?.let { inventoryButtonsJson ->
            inventoryButtonsJson.moveFieldsInto("settings", INVENTORY_BUTTON_SETTINGS_FIELDS)
            inventoryButtonsJson.moveFieldsInto("details", INVENTORY_BUTTON_DETAILS_FIELDS)
        }
        inventoryJson.getObjectOrNull("inventoryEquipment")
            ?.moveFieldsInto("settings", INVENTORY_EQUIPMENT_SETTINGS_FIELDS)
        inventoryJson.getObjectOrNull("fullInventory")?.let { fullInventoryJson ->
            fullInventoryJson.moveFieldsInto("settings", FULL_INVENTORY_SETTINGS_FIELDS)
            fullInventoryJson.moveFieldsInto("details", FULL_INVENTORY_DETAILS_FIELDS)
        }
        inventoryJson.getObjectOrNull("slotBindings")?.let { slotBindingsJson ->
            slotBindingsJson.moveFieldsInto("settings", SLOT_BINDING_SETTINGS_FIELDS)
            slotBindingsJson.moveFieldsInto("details", SLOT_BINDING_DETAILS_FIELDS)
        }
        inventoryJson.getObjectOrNull("smoothSwapping")?.let { smoothSwappingJson ->
            smoothSwappingJson.moveFieldsInto("settings", SMOOTH_SWAPPING_SETTINGS_FIELDS)
            smoothSwappingJson.moveFieldsInto("details", SMOOTH_SWAPPING_DETAILS_FIELDS)
        }
    }

    private fun migrateInventoryEquipmentIntoCategory(json: JsonObject) {
        val inventoryJson = json.getObjectOrNull("inventory") ?: return
        val legacyEnabled = inventoryJson.get("isInventoryEquipmentEnabled") ?: return
        inventoryJson.getOrCreateObject("inventoryEquipment").add("enabled", legacyEnabled.deepCopy())
        inventoryJson.remove("isInventoryEquipmentEnabled")
    }

    private fun migrateEnabledStorageOverlayToModern(json: JsonObject) {
        val inventoryJson = json.getObjectOrNull("inventory") ?: return
        val isEnabled = inventoryJson.get("isStorageOverlayEnabled")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: false
        if (!isEnabled) return
        inventoryJson
            .getOrCreateObject("storageOverlay")
            .getOrCreateObject("settings")
            .addProperty("mode", StorageOverlayMode.MODERN.name)
    }

    private fun migrateBetterShurikensIntoCategory(json: JsonObject) {
        val combatJson = json.getObjectOrNull("combat") ?: return
        val legacyEnabled = combatJson.remove("isBetterShurikensEnabled") ?: return
        val betterShurikensJson = combatJson.getOrCreateObject("betterShurikens")
        if (!betterShurikensJson.has("enabled")) {
            betterShurikensJson.add("enabled", legacyEnabled.deepCopy())
        }
    }

    private fun migrateChatLayout(json: JsonObject) {
        val smoothChatJson = json.getObjectOrNull("chat")?.getObjectOrNull("smoothChat") ?: return
        smoothChatJson.moveFieldsInto("settings", SMOOTH_CHAT_SETTINGS_FIELDS)
        smoothChatJson.moveFieldsInto("details", SMOOTH_CHAT_DETAILS_FIELDS)
    }

    private fun migrateHuntingLayout(json: JsonObject) {
        json.getObjectOrNull("hunting")
            ?.getObjectOrNull("lotumHelper")
            ?.moveFieldsInto("settings", LOTUM_HELPER_SETTINGS_FIELDS)
    }

    private fun migrateFishingLayout(json: JsonObject) {
        val hotspotSharingJson = json.getObjectOrNull("fishing")?.getObjectOrNull("hotspotSharing") ?: return
        hotspotSharingJson.moveFieldsInto("settings", HOTSPOT_SHARING_SETTINGS_FIELDS)
        hotspotSharingJson.moveFieldsInto("details", HOTSPOT_SHARING_DETAILS_FIELDS)
    }

    private fun migrateMiscLayout(json: JsonObject) {
        val miscJson = json.getObjectOrNull("misc") ?: return
        miscJson.get("autoSprint")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.let { legacyEnabled ->
                miscJson.add(
                    "autoSprint",
                    JsonObject().also { autoSprint -> autoSprint.add("enabled", legacyEnabled.deepCopy()) },
                )
            }
        val rareLootValue = miscJson.get("rareLootValue")
        val legacyRareLootSharing = miscJson.get("rareLootSharing")?.takeUnless { it.isJsonObject }
        val rareLootJson = miscJson.getObjectOrNull("rareLootSharing") ?: JsonObject().also {
            miscJson.add("rareLootSharing", it)
        }
        if (!rareLootJson.has("enabled") && legacyRareLootSharing != null) {
            rareLootJson.add("enabled", legacyRareLootSharing.deepCopy())
        }
        if (rareLootValue != null) {
            val settingsJson = rareLootJson.getOrCreateObject("settings")
            if (!settingsJson.has("rareLootValue")) settingsJson.add("rareLootValue", rareLootValue.deepCopy())
            miscJson.remove("rareLootValue")
        }
        miscJson.getObjectOrNull("blockOverlay")
            ?.moveFieldsInto("settings", BLOCK_OVERLAY_SETTINGS_FIELDS)
        miscJson.moveFieldsInto("fixes", MISC_FIX_FIELDS)
        val legacyFixesJson = miscJson.getObjectOrNull("fixes") ?: return
        val fixesJson = json.getOrCreateObject("fixes")
        legacyFixesJson.entrySet().forEach { (fieldName, legacyValue) ->
            if (!fixesJson.has(fieldName)) fixesJson.add(fieldName, legacyValue.deepCopy())
        }
        miscJson.remove("fixes")
    }

    private fun JsonObject.moveFieldsInto(targetName: String, fieldNames: List<String>) {
        val targetJson = getOrCreateObject(targetName)
        fieldNames.forEach { fieldName ->
            val legacyValue = get(fieldName) ?: return@forEach
            if (!targetJson.has(fieldName)) targetJson.add(fieldName, legacyValue.deepCopy())
            remove(fieldName)
        }
    }

    private fun JsonObject.copyFieldInto(target: JsonObject, fieldName: String) {
        val value = get(fieldName) ?: return
        if (!target.has(fieldName)) target.add(fieldName, value.deepCopy())
    }

    private fun migrateObjectIntoSection(
        json: JsonObject,
        legacyName: String,
        sectionName: String,
        targetName: String,
    ) {
        val legacyJson = json.getObjectOrNull(legacyName) ?: return
        val sectionJson = json.getOrCreateObject(sectionName)
        if (!sectionJson.has(targetName)) {
            sectionJson.add(targetName, legacyJson.deepCopy())
        }
        json.remove(legacyName)
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
    private const val PRICE_TOOLTIP_CUSTOMIZATION_VERSION = 5
    private const val INVENTORY_EQUIPMENT_CATEGORY_VERSION = 4
    private const val MODERN_STORAGE_OVERLAY_VERSION = 6
    private const val BETTER_SHURIKENS_CATEGORY_VERSION = 7
    private const val VANILLA_UI_CATEGORY_VERSION = 8
    private const val SELECTIVE_CUSTOM_BAR_ICONS_VERSION = 9
    private const val CUSTOM_BAR_DISPLAY_MODES_VERSION = 10
    private const val MAX_ENCHANT_CHROMA_CATEGORY_VERSION = 11
    private const val SPOTIFY_LYRICS_MODE_VERSION = 12
    private const val SERVER_INFO_METRIC_COLORS_VERSION = 13
    private const val DIANA_FEATURE_ACCORDIONS_VERSION = 15
    private const val DIANA_AND_TERRAIN_SETTINGS_VERSION = 19
    private const val SKYBLOCK_MENU_DROP_FIX_FIELD = "preventSkyBlockMenuOpeningOnInventoryDrop"
}

private fun migrateDianaFeatureAccordions(json: JsonObject) {
    val dianaJson = json.getObjectOrNull("events")?.getObjectOrNull("diana") ?: return
    val globallyEnabled = dianaJson.remove("enabled").booleanPrimitiveOrNull()?.asBoolean ?: false
    val settingsJson = dianaJson.remove("settings")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?: JsonObject()
    val detailsJson = dianaJson.remove("details")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?: JsonObject()

    fun feature(name: String, enabled: Boolean): JsonObject =
        dianaJson.getOrCreateObject(name).also { featureJson ->
            if (!featureJson.has("enabled")) featureJson.addProperty("enabled", enabled)
        }

    val burrowHelper = feature("burrowHelper", globallyEnabled)
    settingsJson.moveFieldsInto(burrowHelper.getOrCreateObject("settings"), DIANA_BURROW_SETTINGS_FIELDS)
    detailsJson.moveFieldsInto(burrowHelper.getOrCreateObject("details"), DIANA_BURROW_DETAILS_FIELDS)

    val shareMobs = settingsJson.remove("rareMobSharing").booleanPrimitiveOrNull()?.asBoolean ?: true
    val rareMobSharing = feature("rareMobSharing", globallyEnabled)
    val rareMobSettings = rareMobSharing.getOrCreateObject("settings")
    if (!rareMobSettings.has("shareMobs")) rareMobSettings.addProperty("shareMobs", shareMobs)
    settingsJson.moveFieldsInto(rareMobSettings, DIANA_RARE_MOB_SETTINGS_FIELDS)
    detailsJson.moveFieldsInto(rareMobSharing.getOrCreateObject("details"), DIANA_RARE_MOB_DETAILS_FIELDS)

    val lobbyCompromised = feature(
        "lobbyCompromised",
        globallyEnabled && (settingsJson.remove("lobbyCompromised").booleanPrimitiveOrNull()?.asBoolean ?: true),
    ).getOrCreateObject("settings")
    settingsJson.moveFieldInto(lobbyCompromised, "lobbyCompromisedStrangerLimit", "strangerLimit")
    settingsJson.moveFieldInto(lobbyCompromised, "lobbyCompromisedAlerts", "alerts")

    feature(
        "sphinxHelper",
        globallyEnabled && (settingsJson.remove("sphinxAnswers").booleanPrimitiveOrNull()?.asBoolean ?: true),
    )

    val quickWarps = feature(
        "quickWarps",
        globallyEnabled && (settingsJson.remove("warpHint").booleanPrimitiveOrNull()?.asBoolean ?: true),
    ).getOrCreateObject("settings")
    settingsJson.moveFieldsInto(quickWarps, DIANA_QUICK_WARP_SETTINGS_FIELDS)

    val keepTerrainLoaded =
        globallyEnabled && (settingsJson.remove("keepHubTerrainLoaded").booleanPrimitiveOrNull()?.asBoolean ?: true)
    val miscJson = json.getOrCreateObject("misc")
    if (!miscJson.has("keepTerrainLoaded")) miscJson.addProperty("keepTerrainLoaded", keepTerrainLoaded)
}

private fun migrateDianaAndTerrainSettings(json: JsonObject) {
    json.getObjectOrNull("misc")?.let { miscJson ->
        val wasEnabled = miscJson.get("keepTerrainLoaded").booleanPrimitiveOrNull()?.asBoolean
        if (wasEnabled != null) {
            miscJson.add(
                "keepTerrainLoaded",
                JsonObject().also { featureJson -> featureJson.addProperty("enabled", wasEnabled) },
            )
        }
        val islands = miscJson.getObjectOrNull("keepTerrainLoaded")
            ?.getObjectOrNull("settings")
            ?.getAsJsonArray("islands")
        if (islands != null) {
            for (index in islands.size() - 1 downTo 0) {
                if (islands[index].asString in NON_PERSISTENT_TERRAIN_ISLANDS) islands.remove(index)
            }
        }
    }

    val diana = json.getObjectOrNull("events")?.getObjectOrNull("diana") ?: return
    val rareMobSharing = diana.getObjectOrNull("rareMobSharing") ?: return
    val lootshare = diana.getObjectOrNull("lootshare")
        ?: rareMobSharing.remove("lootshare")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: JsonObject()
    diana.add("lootshare", lootshare)
    if (!lootshare.has("enabled")) {
        val rareMobSharingEnabled = rareMobSharing.get("enabled").booleanPrimitiveOrNull()?.asBoolean ?: false
        lootshare.addProperty("enabled", rareMobSharingEnabled)
    }
    val settings = lootshare.getOrCreateObject("settings")
    if (!settings.has("shareSecuredMessage")) settings.addProperty("shareSecuredMessage", true)
    val details = lootshare.getOrCreateObject("details")
    details.remove("partyCheckmarks")?.let { partyCheckmarks ->
        if (!settings.has("partyCheckmarks")) settings.add("partyCheckmarks", partyCheckmarks)
    }
    if (!settings.has("partyCheckmarks")) settings.addProperty("partyCheckmarks", true)
    rareMobSharing.getObjectOrNull("settings")?.moveFieldsInto(details, listOf("lootshareRadius"))
    rareMobSharing.getObjectOrNull("details")?.let { legacyDetails ->
        legacyDetails.moveFieldsInto(details, listOf("lootshareMissingColor", "lootshareReadyColor"))
        if (legacyDetails.size() == 0) rareMobSharing.remove("details")
    }

    val partyCommands = diana.getOrCreateObject("partyCommands")
    if (!partyCommands.has("enabled")) {
        val wasEnabled = listOf("burrowHelper", "rareMobSharing", "lobbyCompromised", "sphinxHelper", "quickWarps")
            .any { feature -> diana.getObjectOrNull(feature)?.get("enabled").booleanPrimitiveOrNull()?.asBoolean == true }
        partyCommands.addProperty("enabled", wasEnabled)
    }
}

private fun JsonObject.moveFieldsInto(target: JsonObject, fieldNames: List<String>) {
    fieldNames.forEach { fieldName -> moveFieldInto(target, fieldName, fieldName) }
}

private fun JsonObject.moveFieldInto(target: JsonObject, fieldName: String, targetName: String) {
    val value = remove(fieldName) ?: return
    if (!target.has(targetName)) target.add(targetName, value.deepCopy())
}

private fun com.google.gson.JsonElement?.booleanPrimitiveOrNull(): com.google.gson.JsonPrimitive? =
    this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asJsonPrimitive

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

private const val CURSOR_POSITION_PRESERVATION_CATEGORY_VERSION = 14

private fun migrateCursorPositionPreservation(json: JsonObject, migrationVersion: Int) {
    if (migrationVersion >= CURSOR_POSITION_PRESERVATION_CATEGORY_VERSION) return
    val inventoryJson = json.getObjectOrNull("inventory") ?: return
    val legacyEnabled = inventoryJson.remove("preserveCursorPosition") ?: return
    val featureJson = inventoryJson.getOrCreateObject("cursorPositionPreservation")
    if (!featureJson.has("enabled")) featureJson.add("enabled", legacyEnabled.deepCopy())
}

private val CUSTOM_BAR_RESOURCE_FIELDS = setOf("health", "mana", "vitality", "experience")

private fun migrateSpotifyLyricsMode(json: JsonObject) {
    val details = json.getObjectOrNull("gui")
        ?.getObjectOrNull("spotifyDisplay")
        ?.getObjectOrNull("details")
        ?: return
    val enabled = details.remove("syncedLyrics")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?.asBoolean
        ?: return
    if (!details.has("lyricsMode")) {
        details.addProperty("lyricsMode", if (enabled) SpotifyLyricsMode.FADE.name else SpotifyLyricsMode.OFF.name)
    }
}

private fun migrateServerInfoMetricColors(json: JsonObject) {
    val details = json.getObjectOrNull("gui")
        ?.getObjectOrNull("serverInfoDisplay")
        ?.getObjectOrNull("details")
        ?: return
    val color = details.remove("color") ?: return
    listOf("fpsColor", "tpsColor", "pingColor").forEach { fieldName ->
        if (!details.has(fieldName)) details.add(fieldName, color.deepCopy())
    }
}

private fun migrateCustomBarIcons(json: JsonObject) {
    val details = json.getObjectOrNull("gui")
        ?.getObjectOrNull("customBars")
        ?.getObjectOrNull("details")
        ?: return
    val icons = details.get("icons")?.takeIf { it.isJsonPrimitive }?.asString ?: return
    if (icons != "NONE") return
    details.addProperty("icons", CustomBarIconPosition.LEFT.name)
    CUSTOM_BAR_RESOURCE_FIELDS.forEach { fieldName ->
        val elementDetails = details.getOrCreateObject(fieldName)
        if (!elementDetails.has("showIcon")) elementDetails.addProperty("showIcon", false)
    }
}

private fun migrateCustomBarDisplayModes(json: JsonObject) {
    val settings = json.getObjectOrNull("gui")
        ?.getObjectOrNull("customBars")
        ?.getObjectOrNull("settings")
        ?: return
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
    numbers.remove("speed")?.let { speed ->
        if (!displays.has("speed")) displays.add("speed", speed.deepCopy())
    }
}

private fun JsonObject.moveBooleanToDisplayMode(target: JsonObject, fieldName: String) {
    val value = get(fieldName)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?: return
    if (!target.has(fieldName)) {
        val mode = if (value.asBoolean) CustomBarDisplayMode.CUSTOM else CustomBarDisplayMode.VANILLA
        target.addProperty(fieldName, mode.name)
    }
    remove(fieldName)
}

private fun JsonObject.getObjectOrNull(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.getOrCreateObject(name: String): JsonObject =
    getObjectOrNull(name) ?: JsonObject().also { add(name, it) }

private fun migratePriceTooltipCustomization(json: JsonObject) {
    val settingsJson = json.get("inventory")?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("priceTooltips")?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: return
    val legacyType = settingsJson.remove("bazaarPriceType") ?: return
    if (!settingsJson.has("priceLines")) {
        settingsJson.add("legacyBazaarPriceType", legacyType)
    }
}

private fun JsonObject.migrateHeldItemTextureModes() {
    val legacyItemIds = get("vanillaTextureItemIds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val textureModes = get("itemTextureModes")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also {
        add("itemTextureModes", it)
    }
    val configuredItemIds = textureModes.keySet().mapTo(mutableSetOf()) { it.trim().uppercase(Locale.US) }
    legacyItemIds.forEach { itemIdJson ->
        val itemId = itemIdJson.asString.trim().uppercase(Locale.US)
        if (itemId.isNotEmpty() && configuredItemIds.add(itemId)) {
            textureModes.addProperty(itemId, HeldItemTextureMode.VANILLA.name)
        }
    }
    remove("vanillaTextureItemIds")
}
