package com.skysoft.features.inventory.sacks

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.storedSackAmount
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.formattedText
import net.minecraft.world.item.ItemStack

internal fun sackDisplayItems(stack: ItemStack): List<SackDisplayItem> {
    val itemId = stack.skyBlockId() ?: return emptyList()
    val lore = stack.loreLines()
    val amount = storedSackAmount(lore) ?: return emptyList()
    val gemstoneType = ROUGH_GEMSTONE_ID_PATTERN.matchEntire(itemId)?.groups?.get("type")?.value
    if (gemstoneType != null) return gemstoneDisplayItems(gemstoneType, stack, lore)
    if (amount == 0L) return emptyList()
    val key = SkyBlockDataRepository.itemKey(itemId)
    val name = SkyBlockDataRepository.entry(key)?.formattedDisplayName ?: stack.hoverName.formattedText()
    return listOf(
        SackDisplayItem(
            itemId = itemId,
            name = name,
            amount = amount,
            capacity = storedSackCapacity(lore),
            stack = stack,
        ),
    )
}

private fun gemstoneDisplayItems(
    gemstoneType: String,
    menuStack: ItemStack,
    lore: List<String>,
): List<SackDisplayItem> {
    val gemstoneName = menuStack.hoverName.string.removeSuffix(" Gemstones")
    return lore.mapNotNull { rawLine ->
        val match = GEMSTONE_AMOUNT_PATTERN.find(rawLine.cleanSkyBlockText()) ?: return@mapNotNull null
        val rarity = GemstoneRarity.fromDisplayName(match.groups["rarity"]?.value.orEmpty()) ?: return@mapNotNull null
        val amount = match.groups["amount"]?.value?.replace(",", "")?.toLongOrNull() ?: return@mapNotNull null
        val itemId = "${rarity.name}_${gemstoneType}_GEM"
        val key = SkyBlockDataRepository.itemKey(itemId)
        SackDisplayItem(
            itemId = itemId,
            name = SkyBlockDataRepository.entry(key)?.formattedDisplayName
                ?: "${rarity.color}${rarity.displayName} $gemstoneName Gemstone",
            amount = amount,
            capacity = null,
            stack = SkyBlockDataRepository.displayStack(key),
        )
    }
}

private fun storedSackCapacity(lore: Iterable<String>): String? = lore.asSequence()
    .map { it.cleanSkyBlockText() }
    .mapNotNull { line -> STORED_CAPACITY_PATTERN.matchEntire(line)?.groups?.get("capacity")?.value }
    .firstOrNull()

internal data class SackDisplayItem(
    val itemId: String,
    val name: String,
    val amount: Long,
    val capacity: String?,
    val stack: ItemStack?,
)

private enum class GemstoneRarity(val displayName: String, val color: String) {
    ROUGH("Rough", "§f"),
    FLAWED("Flawed", "§a"),
    FINE("Fine", "§9"),
    FLAWLESS("Flawless", "§5"),
    PERFECT("Perfect", "§6"),
    ;

    companion object {
        fun fromDisplayName(name: String): GemstoneRarity? = entries.firstOrNull {
            it.displayName.equals(name, ignoreCase = true)
        }
    }
}

private val ROUGH_GEMSTONE_ID_PATTERN = Regex("^ROUGH_(?<type>.+)_GEM$")
private val GEMSTONE_AMOUNT_PATTERN = Regex(
    "^(?<rarity>${GemstoneRarity.entries.joinToString("|") { it.displayName }}): (?<amount>[\\d,]+)(?:\\s|$)",
    RegexOption.IGNORE_CASE,
)
private val STORED_CAPACITY_PATTERN = Regex("^Stored: [\\d,]+/(?<capacity>[\\d,.]+[kmb]?)$", RegexOption.IGNORE_CASE)
