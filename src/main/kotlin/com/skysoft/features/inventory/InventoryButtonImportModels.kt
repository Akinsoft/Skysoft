package com.skysoft.features.inventory

import com.skysoft.config.InventoryButtonClickType
import com.skysoft.config.InventoryButtonConfig
import java.nio.file.Path
import java.util.Locale

internal enum class InventoryButtonImportKind(val displayName: String) {
    NEU("NotEnoughUpdates"),
    FIRMAMENT("Firmament"),
}

internal data class InventoryButtonImportSource(
    val kind: InventoryButtonImportKind,
    val path: Path,
    val settingsPath: Path? = null,
    val profileName: String,
)

internal data class InventoryButtonImportSettings(
    val isEnabled: Boolean? = null,
    val clickType: InventoryButtonClickType? = null,
    val tooltipDelay: Int? = null,
)

internal data class InventoryButtonImportReadResult(
    val source: InventoryButtonImportSource,
    val buttons: List<InventoryButtonConfig>,
    val settings: InventoryButtonImportSettings,
    val malformed: Int,
    val substitutedIcons: Int,
    val unsupportedIcons: Int,
    val giganticButtons: Int,
    val unsupportedSettings: List<String>,
)

internal data class InventoryButtonImportPlan(
    val read: InventoryButtonImportReadResult,
    val mergedButtons: MutableList<InventoryButtonConfig>,
    val imported: Int,
    val duplicates: Int,
    val conflicts: Int,
) {
    val canMerge: Boolean get() = imported > 0
    val canReplace: Boolean get() = read.buttons.isNotEmpty() && duplicates + conflicts > 0
}

internal data class InventoryButtonImportSnapshot(
    val buttons: MutableList<InventoryButtonConfig>,
    val isEnabled: Boolean,
    val clickType: InventoryButtonClickType,
    val tooltipDelay: Int,
)

internal data class ImportedIcon(
    val value: String?,
    val isSubstituted: Boolean = false,
    val isUnsupported: Boolean = false,
)

internal fun normalizeImportedInventoryButtonIcon(rawIcon: String?): ImportedIcon {
    val icon = rawIcon?.trim()?.takeIf(String::isNotEmpty) ?: return ImportedIcon(null)
    inventoryButtonTextureHash(icon)?.let { hash ->
        return ImportedIcon("skull:$hash")
    }
    if (icon.startsWith("skull:", ignoreCase = true)) {
        return ImportedIcon(null, isUnsupported = true)
    }
    if (icon.startsWith("extra:", ignoreCase = true)) {
        return NEU_EXTRA_ICON_ALIASES[icon.substringAfter(':').lowercase(Locale.ROOT)]?.let {
            ImportedIcon(it, isSubstituted = true)
        } ?: ImportedIcon(null, isUnsupported = true)
    }
    if (icon.startsWith("/give ", ignoreCase = true) || icon.startsWith("give ", ignoreCase = true)) {
        return normalizeImportedGiveIcon(icon)
    }
    return ImportedIcon(icon)
}

private fun normalizeImportedGiveIcon(icon: String): ImportedIcon {
    PLAYER_HEAD_PROFILE_NAME.find(icon)?.groupValues?.get(1)?.let {
        return ImportedIcon("player:$it", isSubstituted = true)
    }
    val item = icon.removePrefix("/")
        .split(Regex("\\s+"))
        .drop(1)
        .firstOrNull { !it.startsWith('@') }
        ?.substringBefore('[')
        ?.substringBefore('{')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    return if (item == null) ImportedIcon(null, isUnsupported = true) else ImportedIcon(item, isSubstituted = true)
}

internal fun inventoryButtonTextureHash(icon: String): String? {
    if (!icon.startsWith("skull:", ignoreCase = true)) return null
    val hash = icon.substringAfter(':')
    return hash.takeIf(TEXTURE_HASH::matches)?.lowercase(Locale.ROOT)
}

internal fun InventoryButtonConfig.copyForImport(): InventoryButtonConfig = InventoryButtonConfig(
    x = x,
    y = y,
    icon = icon,
    playerInvOnly = playerInvOnly,
    anchorRight = anchorRight,
    anchorBottom = anchorBottom,
    backgroundIndex = backgroundIndex,
    command = command,
)

private val TEXTURE_HASH = Regex("^[0-9a-fA-F]{32,128}$")
private val PLAYER_HEAD_PROFILE_NAME = Regex(
    """(?i)(?:minecraft:)?player_head\[[^]]*(?:minecraft:)?profile\s*=\s*\{[^}]*name\s*:\s*["']?([A-Za-z0-9_]{3,16})""",
)

private val NEU_EXTRA_ICON_ALIASES = mapOf(
    "accessory" to "minecraft:emerald",
    "accessory_gold" to "minecraft:emerald",
    "armor" to "minecraft:leather_chestplate",
    "armor_gold" to "minecraft:golden_chestplate",
    "baubles" to "minecraft:leather_chestplate",
    "baubles_gold" to "minecraft:golden_chestplate",
    "cross" to "minecraft:barrier",
    "green_check" to "minecraft:lime_dye",
    "pet" to "minecraft:bone",
    "pet_gold" to "minecraft:bone",
    "question" to "minecraft:book",
    "recipe" to "minecraft:crafting_table",
    "search" to "minecraft:compass",
    "settings" to "minecraft:redstone_torch",
    "skyblock_menu" to "minecraft:nether_star",
    "white_check" to "minecraft:white_dye",
)
