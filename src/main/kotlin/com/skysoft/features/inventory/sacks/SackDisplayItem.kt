package com.skysoft.features.inventory.sacks

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.sackMenuItems
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.formattedText
import net.minecraft.world.item.ItemStack

internal fun sackDisplayItems(stack: ItemStack): List<SackDisplayItem> = sackMenuItems(stack).mapNotNull { item ->
    val key = SkyBlockDataRepository.itemKey(item.itemId)
    val name = SkyBlockDataRepository.entry(key)?.formattedDisplayName
    val rarity = item.gemstoneRarity
    when {
        rarity != null -> SackDisplayItem(
            itemId = item.itemId,
            name = name ?: "${rarity.color}${item.displayName}",
            amount = item.amount,
            capacity = null,
            stack = SkyBlockDataRepository.displayStack(key),
        )
        item.amount == 0L -> null
        else -> SackDisplayItem(
            itemId = item.itemId,
            name = name ?: stack.hoverName.formattedText(),
            amount = item.amount,
            capacity = storedSackCapacity(stack.loreLines()),
            stack = stack,
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

private val STORED_CAPACITY_PATTERN = Regex("^Stored: [\\d,]+/(?<capacity>[\\d,.]+[kmb]?)$", RegexOption.IGNORE_CASE)
