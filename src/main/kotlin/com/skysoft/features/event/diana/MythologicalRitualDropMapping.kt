package com.skysoft.features.event.diana

import com.skysoft.features.loot.RareLootChatDrop
import com.skysoft.features.loot.bestItemId
import com.skysoft.features.loot.toItemIdKey
import java.util.Locale

internal data class MythologicalRitualTrackedDrop(
    val itemKey: String,
    val amount: Long,
    val magicFindKey: String? = null,
    val magicFind: Int = 0,
)

internal object MythologicalRitualDropMapping {
    fun fromChatDrop(drop: RareLootChatDrop, lootshare: Boolean): MythologicalRitualTrackedDrop? {
        val baseKey = baseItemKey(drop) ?: return null
        val itemKey = if (lootshare) lootshareItemIds[baseKey] ?: baseKey else baseKey
        return MythologicalRitualTrackedDrop(
            itemKey = itemKey,
            amount = drop.amount.coerceAtLeast(1).toLong(),
            magicFindKey = magicFindItemIds[baseKey],
            magicFind = drop.context.magicFind(),
        )
    }

    fun priceItemId(itemKey: String): String? =
        priceItemIds[itemKey] ?: itemKey.takeUnless { it in unpricedItemKeys }

    private fun baseItemKey(drop: RareLootChatDrop): String? {
        val itemId = drop.bestItemId()?.uppercase(Locale.US)
        if (itemId != null) itemIdKey(itemId)?.let { return it }
        return nameKey(drop.displayName)
    }

    private fun itemIdKey(itemId: String): String? =
        when {
            itemId.contains("CHIMERA") -> MythologicalRitualItemKey.CHIMERA
            else -> directItemIds[itemId]
        }

    private fun nameKey(displayName: String): String? =
        directItemIds[displayName.toItemIdKey()]

    private fun String?.magicFind(): Int {
        val context = this ?: return 0
        return magicFindPattern.find(context)
            ?.groups
            ?.get("magicFind")
            ?.value
            ?.toIntOrNull()
            ?: 0
    }

    private val directItemIds = mapOf(
        "DAEDALUS_STICK" to MythologicalRitualItemKey.DAEDALUS_STICK,
        "MINOS_RELIC" to MythologicalRitualItemKey.MINOS_RELIC,
        "MANTI_CORE" to MythologicalRitualItemKey.MANTI_CORE,
        "FATEFUL_STINGER" to MythologicalRitualItemKey.FATEFUL_STINGER,
        "SHIMMERING_WOOL" to MythologicalRitualItemKey.SHIMMERING_WOOL,
        "BRAIN_FOOD" to MythologicalRitualItemKey.BRAIN_FOOD,
        "MYTHOS_FRAGMENT" to MythologicalRitualItemKey.MYTHOS_FRAGMENT,
        "CRETAN_URN" to MythologicalRitualItemKey.CRETAN_URN,
        "HILT_OF_REVELATIONS" to MythologicalRitualItemKey.HILT_OF_REVELATIONS,
        "GRIFFIN_FEATHER" to MythologicalRitualItemKey.GRIFFIN_FEATHER,
        "BRAIDED_GRIFFIN_FEATHER" to MythologicalRitualItemKey.BRAIDED_GRIFFIN_FEATHER,
        "MYTH_THE_FISH" to MythologicalRitualItemKey.MYTH_THE_FISH,
        "CROWN_OF_GREED" to MythologicalRitualItemKey.CROWN_OF_GREED,
        "MYTHOLOGICAL_DYE" to MythologicalRitualItemKey.MYTHOLOGICAL_DYE,
    )

    private val lootshareItemIds = mapOf(
        MythologicalRitualItemKey.CHIMERA to MythologicalRitualItemKey.CHIMERA_LS,
        MythologicalRitualItemKey.MANTI_CORE to MythologicalRitualItemKey.MANTI_CORE_LS,
        MythologicalRitualItemKey.FATEFUL_STINGER to MythologicalRitualItemKey.FATEFUL_STINGER_LS,
        MythologicalRitualItemKey.SHIMMERING_WOOL to MythologicalRitualItemKey.SHIMMERING_WOOL_LS,
        MythologicalRitualItemKey.BRAIN_FOOD to MythologicalRitualItemKey.BRAIN_FOOD_LS,
    )

    private val magicFindItemIds = mapOf(
        MythologicalRitualItemKey.CHIMERA to MythologicalRitualMagicFindKey.CHIMERA,
        MythologicalRitualItemKey.DAEDALUS_STICK to MythologicalRitualMagicFindKey.STICK,
        MythologicalRitualItemKey.MINOS_RELIC to MythologicalRitualMagicFindKey.RELIC,
        MythologicalRitualItemKey.MANTI_CORE to MythologicalRitualMagicFindKey.CORE,
        MythologicalRitualItemKey.FATEFUL_STINGER to MythologicalRitualMagicFindKey.STINGER,
        MythologicalRitualItemKey.SHIMMERING_WOOL to MythologicalRitualMagicFindKey.WOOL,
        MythologicalRitualItemKey.BRAIN_FOOD to MythologicalRitualMagicFindKey.FOOD,
    )

    private val priceItemIds = mapOf(
        MythologicalRitualItemKey.CHIMERA to "ENCHANTMENT_ULTIMATE_CHIMERA_1",
        MythologicalRitualItemKey.CHIMERA_LS to "ENCHANTMENT_ULTIMATE_CHIMERA_1",
        MythologicalRitualItemKey.KING_MINOS_SHARD to "SHARD_KING_MINOS",
        MythologicalRitualItemKey.SPHINX_SHARD to "SHARD_SPHINX",
        MythologicalRitualItemKey.MINOTAUR_SHARD to "SHARD_MINOTAUR",
        MythologicalRitualItemKey.CRETAN_BULL_SHARD to "SHARD_CRETAN_BULL",
        MythologicalRitualItemKey.HARPY_SHARD to "SHARD_HARPY",
    )

    private val unpricedItemKeys = setOf(
        MythologicalRitualItemKey.COINS,
        MythologicalRitualItemKey.TOTAL_BURROWS,
    )

    private val magicFindPattern = Regex("""\+(?<magicFind>\d+)\s+.*MF""", RegexOption.IGNORE_CASE)
}
