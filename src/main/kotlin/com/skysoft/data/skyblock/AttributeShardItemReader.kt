package com.skysoft.data.skyblock

import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.removeColor
import net.minecraft.world.item.ItemStack
import java.util.Locale

internal object AttributeShardItemReader {
    const val HUNTING_BOX = "Hunting Box"
    const val ATTRIBUTE_MENU = "Attribute Menu"

    fun internalNameOrNull(item: ItemStack, inventoryName: String?): String? {
        if (isAttributeShardInventoryName(inventoryName)) {
            resolveContextualShardInternalName(item, inventoryName)?.let { return it }
        }

        item.skyBlockId()
            ?.let { AttributeShardConstants.internalNameFromKnownShardId(it) }
            ?.let { return it }

        val extraAttributes = item.extraAttributes() ?: return resolveContextualShardInternalName(item, inventoryName)
        val id = extraAttributes.getStringOrNull("id")?.uppercase(Locale.US)?.let(::canonicalSkyBlockItemId)
        return if (id == "ATTRIBUTE_SHARD") {
            extraAttributes.attributeShardId() ?: resolveContextualShardInternalName(item, inventoryName)
        } else {
            id?.let { AttributeShardConstants.internalNameFromKnownShardId(it) }
                ?: resolveContextualShardInternalName(item, inventoryName)
        }
    }

    fun isAttributeMenuName(inventoryName: String?): Boolean =
        inventoryName == ATTRIBUTE_MENU || inventoryName?.startsWith("$ATTRIBUTE_MENU ") == true

    fun hasAttributeStateLine(item: ItemStack): Boolean = item.loreLines().any { enabledState(it) != null }

    fun tier(item: ItemStack): Int {
        val hoverName = item.formattedHoverName()
        val match = attributeShardNamePattern.matchEntire(hoverName)
            ?: cleanAttributeShardNamePattern.matchEntire(hoverName.cleanSkyBlockText())
        return match?.groupOrNull("tier")?.romanToDecimal() ?: 0
    }

    fun tierFromLore(line: String): Int? {
        val match = attributeShardNameLorePattern.matchEntire(line)
            ?: cleanAttributeShardNameLorePattern.matchEntire(line.cleanSkyBlockText())
            ?: return null
        return match.groupOrNull("tier")?.romanToDecimal() ?: 0
    }

    fun enabledState(line: String): Boolean? {
        val state = attributeStatePattern.matchEntire(line)?.group("state")
            ?: cleanAttributeStatePattern.matchEntire(line.removeColor())?.group("state")
            ?: return null
        return isEnabledAttributeState(state)
    }

    private fun isEnabledAttributeState(state: String): Boolean =
        enabledAttributeStates.any { state.trim().equals(it, ignoreCase = true) }

    private fun resolveContextualShardInternalName(item: ItemStack, inventoryName: String?): String? {
        val cleanName = item.formattedHoverName().cleanSkyBlockText().removeSuffix(" NEW SHARD").trim()
        val shardName = when {
            isAttributeMenuName(inventoryName) -> findAttributeMenuShardName(item, cleanName)
            inventoryName == HUNTING_BOX -> findHuntingBoxShardName(item, cleanName)
            else -> null
        } ?: return null
        return AttributeShardConstants.internalNameByBazaarName(shardName)
    }

    private fun findAttributeMenuShardName(item: ItemStack, cleanName: String): String? =
        shardNameFromCandidates(cleanName) ?: item.loreLines().firstNotNullOfOrNull { line ->
            val cleanLine = line.cleanSkyBlockText()
            val source = attributeSourcePattern.matchEntire(line)?.group("source")
                ?: cleanAttributeSourcePattern.matchEntire(cleanLine)?.group("source")
            source?.let(AttributeShardConstants::shardByDisplayOrAbilityName) ?: shardNameFromCandidates(cleanLine)
        }

    private fun findHuntingBoxShardName(item: ItemStack, cleanName: String): String? =
        shardNameFromCandidates(cleanName)
            ?: item.loreLines().firstNotNullOfOrNull { line -> shardNameFromCandidates(line.cleanSkyBlockText()) }

    private fun shardNameFromCandidates(cleanName: String): String? =
        listOfNotNull(
            cleanName,
            cleanAttributeShardNamePattern.matchEntire(cleanName)?.group("name")?.trim(),
            cleanAttributeShardNameLorePattern.matchEntire(cleanName)?.group("name")?.trim(),
        ).distinct().firstNotNullOfOrNull(AttributeShardConstants::shardByDisplayOrAbilityName)

    private fun isAttributeShardInventoryName(inventoryName: String?): Boolean =
        isAttributeMenuName(inventoryName) || inventoryName == HUNTING_BOX
}

private val enabledAttributeStates = listOf("Yes", "On", "Enabled")

private val attributeShardNamePattern = Regex("""§6(?<name>.+?) ?(?<tier>[IVXL]+)?$""")
private val cleanAttributeShardNamePattern = Regex("""(?<name>.+?) ?(?<tier>[IVXL]+)?$""")
private val attributeShardNameLorePattern = Regex("""§6(?<name>.+?) ?(?<tier>[IVXL]+)? §8\(\w+\)$""")
private val cleanAttributeShardNameLorePattern = Regex("""(?<name>.+?) ?(?<tier>[IVXL]+)? \(\w+\)$""")
private val attributeStatePattern = Regex("""§7Enabled: §.(?<state>.+)""")
private val cleanAttributeStatePattern = Regex("""Enabled: (?<state>.+)""")
private val attributeSourcePattern = Regex("""§7Source: §.(?<source>.+)""")
private val cleanAttributeSourcePattern = Regex("""Source: (?<source>.+)""")
