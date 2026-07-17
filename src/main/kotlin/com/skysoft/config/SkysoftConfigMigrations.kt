package com.skysoft.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import java.util.Locale

internal object SkysoftConfigMigrations {
    const val CURRENT_CONFIG_MIGRATION_VERSION = 3

    fun apply(json: JsonObject, gson: Gson) {
        val migrationVersion = json.get(CONFIG_MIGRATION_VERSION_FIELD)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: 0
        importLegacyStorage(json, gson)
        migrateBazaarIntoInventory(json)
        migrateActionBarBackgroundIntoGui(json)
        migrateDianaSettings(json)
        migrateRareLootSharingIntoMisc(json)
        migrateBuggedNameplatesIntoMisc(json)
        migrateOrganizedConfigLayout(json)
        if (migrationVersion < CONFIG_MENU_ORGANIZATION_VERSION) {
            ConfigMenuOrganizationMigration.apply(json)
        }
        if (migrationVersion < MENU_DROP_FIX_SAFETY_VERSION) {
            json.getObjectOrNull("fixes")
                ?.takeIf { it.has(SKYBLOCK_MENU_DROP_FIX_FIELD) }
                ?.addProperty(SKYBLOCK_MENU_DROP_FIX_FIELD, false)
        }
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

    private fun JsonObject.getObjectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.getOrCreateObject(name: String): JsonObject =
        getObjectOrNull(name) ?: JsonObject().also { add(name, it) }

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
    private const val CONFIG_MIGRATION_VERSION_FIELD = "configMigrationVersion"
    private const val MENU_DROP_FIX_SAFETY_VERSION = 1
    private const val CONFIG_MENU_ORGANIZATION_VERSION = 3
    private const val SKYBLOCK_MENU_DROP_FIX_FIELD = "preventSkyBlockMenuOpeningOnInventoryDrop"
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
