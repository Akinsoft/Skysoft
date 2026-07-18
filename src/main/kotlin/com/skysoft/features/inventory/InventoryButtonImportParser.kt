package com.skysoft.features.inventory

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.config.InventoryButtonClickType
import com.skysoft.config.InventoryButtonConfig
import java.nio.file.Files

internal object InventoryButtonImportParser {
    fun read(source: InventoryButtonImportSource): InventoryButtonImportReadResult = when (source.kind) {
        InventoryButtonImportKind.NEU -> readNeu(source)
        InventoryButtonImportKind.FIRMAMENT -> readFirmament(source)
    }

    private fun readNeu(source: InventoryButtonImportSource): InventoryButtonImportReadResult {
        val root = readJson(source.path)
        val config = root.get("inventoryButtons")?.takeIf { it.isJsonObject }?.asJsonObject
        val hidden = root.get("hidden")?.takeIf { it.isJsonObject }?.asJsonObject
        val buttons = hidden?.get("inventoryButtons")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw InventoryButtonImportException("NEU inventory buttons were not found in ${source.path}")
        val parsed = readButtons(buttons, InventoryButtonImportKind.NEU, isPlayerInventoryOnly = false)
        val unsupportedSettings = buildList {
            if (config.boolean("hideCrafting") == true) add("Always Hide Crafting Text")
            if (config.boolean("hideInDungeonMenus") == true) add("Hide Buttons in Dungeon Menus")
        }
        return parsed.result(
            source,
            InventoryButtonImportSettings(
                isEnabled = config.boolean("enableInventoryButtons"),
                clickType = when (config.integer("clickType")) {
                    0 -> InventoryButtonClickType.MOUSE_DOWN
                    1 -> InventoryButtonClickType.MOUSE_UP
                    else -> null
                },
                tooltipDelay = config.integer("tooltipDelay")?.coerceIn(0, MAX_TOOLTIP_DELAY),
            ),
            unsupportedSettings,
        )
    }

    private fun readFirmament(source: InventoryButtonImportSource): InventoryButtonImportReadResult {
        val root = readJson(source.path)
        val buttons = root.get("buttons")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.get("inventory-buttons")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("buttons")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw InventoryButtonImportException("Firmament inventory buttons were not found in ${source.path}")
        val settingsRoot = source.settingsPath?.takeIf(Files::isRegularFile)?.let(::readJson)
        val settings = settingsRoot?.get("inventory-buttons-config")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: settingsRoot
        val isPlayerInventoryOnly = settings.boolean("only-inv") == true
        val parsed = readButtons(buttons, InventoryButtonImportKind.FIRMAMENT, isPlayerInventoryOnly)
        val unsupportedSettings = buildList {
            if (settings.boolean("hover-text") == false) add("Disabled Hover Text")
        }
        return parsed.result(
            source,
            InventoryButtonImportSettings(clickType = InventoryButtonClickType.MOUSE_DOWN),
            unsupportedSettings,
        )
    }

    private fun readButtons(
        array: JsonArray,
        kind: InventoryButtonImportKind,
        isPlayerInventoryOnly: Boolean,
    ): ParsedButtons {
        val parsed = mutableListOf<InventoryButtonConfig>()
        var malformed = (array.size() - MAX_IMPORTED_BUTTONS).coerceAtLeast(0)
        var substitutedIcons = 0
        var unsupportedIcons = 0
        var giganticButtons = 0
        array.take(MAX_IMPORTED_BUTTONS).forEach { element ->
            val raw = runCatching { GSON.fromJson(element, RawInventoryButton::class.java) }.getOrNull()
            if (raw == null || !raw.isValid(kind)) {
                malformed++
                return@forEach
            }
            if (raw.command.isNullOrBlank()) return@forEach
            val icon = normalizeImportedInventoryButtonIcon(raw.icon)
            if (icon.isSubstituted) substitutedIcons++
            if (icon.isUnsupported) unsupportedIcons++
            if (raw.isGigantic) giganticButtons++
            parsed += InventoryButtonConfig(
                x = raw.x,
                y = raw.y,
                icon = icon.value,
                playerInvOnly = if (kind == InventoryButtonImportKind.NEU) raw.playerInvOnly else isPlayerInventoryOnly,
                anchorRight = raw.anchorRight,
                anchorBottom = raw.anchorBottom,
                backgroundIndex = raw.backgroundIndex.coerceIn(0, MAX_BACKGROUND_INDEX),
                command = raw.command.orEmpty().trim().removePrefix("/"),
                isUserCreated = false,
            )
        }
        return ParsedButtons(parsed, malformed, substitutedIcons, unsupportedIcons, giganticButtons)
    }

    private fun readJson(path: java.nio.file.Path): JsonObject {
        val size = Files.size(path)
        if (size > MAX_IMPORT_FILE_BYTES) throw InventoryButtonImportException("Config is larger than 2 MB: $path")
        return try {
            Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonObject }
        } catch (exception: Exception) {
            throw InventoryButtonImportException("Could not read $path: ${exception.message}")
        }
    }

    private data class ParsedButtons(
        val buttons: List<InventoryButtonConfig>,
        val malformed: Int,
        val substitutedIcons: Int,
        val unsupportedIcons: Int,
        val giganticButtons: Int,
    ) {
        fun result(
            source: InventoryButtonImportSource,
            settings: InventoryButtonImportSettings,
            unsupportedSettings: List<String>,
        ) = InventoryButtonImportReadResult(
            source,
            buttons,
            settings,
            malformed,
            substitutedIcons,
            unsupportedIcons,
            giganticButtons,
            unsupportedSettings,
        )
    }

    private class RawInventoryButton {
        var x: Int = 0
        var y: Int = 0
        var playerInvOnly: Boolean = false
        var anchorRight: Boolean = false
        var anchorBottom: Boolean = false
        var backgroundIndex: Int = 0
        var command: String? = null
        var icon: String? = null
        var isGigantic: Boolean = false

        fun isValid(kind: InventoryButtonImportKind): Boolean =
            x in -MAX_COORDINATE..MAX_COORDINATE &&
                y in -MAX_COORDINATE..MAX_COORDINATE &&
                command.orEmpty().length <= MAX_COMMAND_LENGTH &&
                icon.orEmpty().length <= MAX_ICON_LENGTH &&
                (kind != InventoryButtonImportKind.FIRMAMENT || !icon.isNullOrBlank())
    }

    private fun JsonObject?.boolean(name: String): Boolean? =
        runCatching { this?.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()

    private fun JsonObject?.integer(name: String): Int? =
        runCatching { this?.get(name)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

    private const val MAX_IMPORT_FILE_BYTES = 2L * 1024L * 1024L
    private const val MAX_IMPORTED_BUTTONS = 256
    private const val MAX_COORDINATE = 4096
    private const val MAX_COMMAND_LENGTH = 256
    private const val MAX_ICON_LENGTH = 1024
    private const val MAX_TOOLTIP_DELAY = 1500
    private const val MAX_BACKGROUND_INDEX = 6
    private val GSON = Gson()
}

internal class InventoryButtonImportException(message: String) : Exception(message)
