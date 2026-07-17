package com.skysoft.config

import com.google.gson.JsonObject

internal object ConfigMenuOrganizationMigration {
    fun apply(json: JsonObject) {
        migratePets(json)
        migrateGui(json)
        migrateInventory(json)
        migrateCombat(json)
        migrateChat(json)
        migrateFishing(json)
        migrateMisc(json)
    }

    private fun migratePets(json: JsonObject) {
        json.getObjectOrNull("misc")?.let { miscJson ->
            miscJson.getObjectOrNull("pets")?.let { petsJson ->
                if (json.getObjectOrNull("pets") == null) json.add("pets", petsJson.deepCopy())
                miscJson.remove("pets")
            }
        }
        val petsJson = json.getObjectOrNull("pets") ?: return
        petsJson.getObjectOrNull("visiblePetPosition")?.let { visiblePetPositionJson ->
            visiblePetPositionJson.moveFieldsInto("settings", "stopBouncing")
            visiblePetPositionJson.moveFieldsInto("details", "heightOffset")
        }
        petsJson.getObjectOrNull("petDisplay")
            ?.getObjectOrNull("display")
            ?.getObjectOrNull("general")
            ?.moveFieldsInto("settings", "hideInMenus")
    }

    private fun migrateGui(json: JsonObject) {
        val guiJson = json.getObjectOrNull("gui") ?: return
        guiJson.getObjectOrNull("positionEditor")
            ?.moveFieldInto(guiJson, "renderTitlesInFront", "areTitlesRenderedInFront")
        guiJson.getObjectOrNull("heldItem")
            ?.moveFieldsInto("settings", "ignoresMiningEffects")
        guiJson.getObjectOrNull("actionBar")?.let { actionBarJson ->
            actionBarJson.moveFieldsInto("details", "roundedCorners", "backgroundOpacity")
        }
    }

    private fun migrateInventory(json: JsonObject) {
        val inventoryJson = json.getOrCreateObject("inventory")
        val miscJson = json.getObjectOrNull("misc")
        miscJson?.moveFieldInto(inventoryJson, "solidTooltipBackground", "isTooltipBackgroundSolid")
        miscJson?.moveFieldInto(inventoryJson, "hideVanillaRecipeBook", "isVanillaRecipeBookHidden")
        inventoryJson.getObjectOrNull("storageOverlay")
            ?.moveFieldInto(inventoryJson, "enabled", "isStorageOverlayEnabled")
        inventoryJson.getObjectOrNull("itemList")?.let { itemListJson ->
            val sourcesJson = itemListJson.getObjectOrNull("sources") ?: return@let
            val settingsJson = itemListJson.getOrCreateObject("settings")
            sourcesJson.moveFieldInto(settingsJson, "showVanilla")
            sourcesJson.moveFieldInto(settingsJson, "isRightClickClearEnabled")
        }
        inventoryJson.getObjectOrNull("protectItem")?.let { protectItemJson ->
            protectItemJson.getObjectOrNull("settings")?.let { settingsJson ->
                settingsJson.moveFieldsInto(
                    protectItemJson.getOrCreateObject("details"),
                    "showProtectedItemStar",
                    "protectedItemStarColor",
                    "protectedItemStarScale",
                    "protectedItemStarOpacity",
                )
            }
        }
        inventoryJson.getObjectOrNull("bazaar")?.let { bazaarJson ->
            bazaarJson.getObjectOrNull("details")
                ?.moveFieldInto(bazaarJson.getOrCreateObject("settings"), "onlyMyOrders")
        }
    }

    private fun migrateCombat(json: JsonObject) {
        val combatJson = json.getObjectOrNull("combat") ?: return
        combatJson.getObjectOrNull("betterShurikens")?.let { betterShurikensJson ->
            betterShurikensJson.moveFieldInto(combatJson, "enabled", "isBetterShurikensEnabled")
            combatJson.remove("betterShurikens")
        }
    }

    private fun migrateChat(json: JsonObject) {
        val chatJson = json.getObjectOrNull("chat") ?: return
        val historyJson = chatJson.getOrCreateObject("history")
        chatJson.getObjectOrNull("longerHistory")?.let { longerHistoryJson ->
            longerHistoryJson.moveFieldInto(historyJson, "enabled", "isLongerHistoryEnabled")
            longerHistoryJson.moveFieldInto(historyJson, "messageLimit")
            chatJson.remove("longerHistory")
        }
        chatJson.moveFieldInto(historyJson, "retainHistory", "isHistoryRetained")
        historyJson.moveFieldsInto("settings", "messageLimit")
        chatJson.getObjectOrNull("compacting")
            ?.moveFieldsInto("settings", "durationSeconds", "noBlankLines")
        chatJson.getObjectOrNull("notify")
            ?.moveFieldsInto("settings", "words")
        chatJson.getObjectOrNull("tabs")
            ?.moveFieldsInto("settings", "position", "channels")
        chatJson.getObjectOrNull("timestamps")
            ?.moveFieldsInto("settings", "format")
        chatJson.getObjectOrNull("copyChat")
            ?.moveFieldsInto("settings", "key")
    }

    private fun migrateFishing(json: JsonObject) {
        json.getObjectOrNull("fishing")
            ?.getObjectOrNull("hotspotRadar")
            ?.moveFieldsInto("details", "crosshairLine")
    }

    private fun migrateMisc(json: JsonObject) {
        val blockOverlayJson = json.getObjectOrNull("misc")?.getObjectOrNull("blockOverlay") ?: return
        blockOverlayJson.getObjectOrNull("settings")
            ?.moveFieldInto(blockOverlayJson.getOrCreateObject("details"), "color")
    }

    private fun JsonObject.moveFieldsInto(targetName: String, vararg fieldNames: String) {
        moveFieldsInto(getOrCreateObject(targetName), *fieldNames)
    }

    private fun JsonObject.moveFieldsInto(target: JsonObject, vararg fieldNames: String) {
        fieldNames.forEach { fieldName -> moveFieldInto(target, fieldName) }
    }

    private fun JsonObject.moveFieldInto(
        target: JsonObject,
        fieldName: String,
        targetFieldName: String = fieldName,
    ) {
        val legacyValue = get(fieldName) ?: return
        if (!target.has(targetFieldName)) target.add(targetFieldName, legacyValue.deepCopy())
        remove(fieldName)
    }

    private fun JsonObject.getObjectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.getOrCreateObject(name: String): JsonObject =
        getObjectOrNull(name) ?: JsonObject().also { add(name, it) }
}
