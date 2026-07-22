package com.skysoft.data.skyblock

import com.skysoft.features.pets.setSkyBlockId
import kotlin.math.floor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

internal object SkyBlockPetStacks {
    private val cache = object : LinkedHashMap<String, ItemStack>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ItemStack>?): Boolean = size > CACHE_SIZE
    }

    fun stack(
        ingredientId: String,
        level: Int,
        pets: Map<String, SkyBlockPetInfo>,
        maxLevels: Map<String, Int>,
    ): ItemStack? {
        val identity = petIdentity(ingredientId, maxLevels) ?: return null
        val pet = pets[identity.name] ?: return null
        val tier = pet.tiers[identity.rarity.name] ?: return null
        if (tier.texture.isBlank()) return null
        val selectedLevel = level.coerceIn(1, identity.maxLevel)
        val cacheKey = "$ingredientId:$selectedLevel"
        synchronized(cache) {
            cache[cacheKey]?.let { return it.copy() }
        }
        val tooltip = tooltip(ingredientId, selectedLevel, pets, maxLevels) ?: return null
        val stack = SkyBlockStackFactory.texturedHead(tier.texture, Component.literal(tooltip.name)).apply {
            set(
                DataComponents.LORE,
                ItemLore(tooltip.lore.map(Component::literal)),
            )
            setSkyBlockId("${identity.name};${identity.rarity.id}")
        }
        synchronized(cache) {
            cache[cacheKey] = stack
        }
        return stack.copy()
    }

    fun tooltip(
        ingredientId: String,
        level: Int,
        pets: Map<String, SkyBlockPetInfo>,
        maxLevels: Map<String, Int>,
    ): SkyBlockPetTooltip? {
        val identity = petIdentity(ingredientId, maxLevels) ?: return null
        val pet = pets[identity.name] ?: return null
        val tier = pet.tiers[identity.rarity.name] ?: return null
        val selectedLevel = level.coerceIn(1, identity.maxLevel)
        val replacements = tier.variables.mapValues { (_, range) ->
            formatPetVariable(range, selectedLevel, identity.maxLevel, tier.variablesOffset)
        }
        return SkyBlockPetTooltip(
            name = "§7[Lvl $selectedLevel] ${identity.rarity.chatColorCode}${pet.name.ifBlank { identity.name }}",
            lore = tier.lore.map { line -> replacePetVariables(line, replacements) },
        )
    }

    fun maxLevel(ingredientId: String, maxLevels: Map<String, Int>): Int =
        petIdentity(ingredientId, maxLevels)?.maxLevel ?: DEFAULT_MAX_LEVEL

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    private fun petIdentity(ingredientId: String, maxLevels: Map<String, Int>): PetIdentity? {
        val separator = ingredientId.lastIndexOf(';')
        if (separator <= 0 || separator == ingredientId.lastIndex) return null
        val name = ingredientId.substring(0, separator)
        val rarity = SkyBlockRarity.getByName(ingredientId.substring(separator + 1)) ?: return null
        return PetIdentity(name, rarity, maxLevels[name] ?: DEFAULT_MAX_LEVEL)
    }

    private fun formatPetVariable(
        range: List<Double>,
        level: Int,
        maxLevel: Int,
        offset: Int,
    ): String {
        val minimum = range.getOrNull(0) ?: return "?"
        val maximum = range.getOrNull(1) ?: minimum
        val value = when {
            offset > 0 && level <= offset -> 0.0
            else -> {
                val effectiveLevel = (level - offset).coerceAtLeast(1)
                val effectiveMaximum = (maxLevel - offset).coerceAtLeast(1)
                val progress = if (effectiveMaximum == 1) 1.0 else {
                    (effectiveLevel - 1).toDouble() / (effectiveMaximum - 1)
                }
                minimum + (maximum - minimum) * progress.coerceIn(0.0, 1.0)
            }
        }
        val rounded = floor((value + ROUNDING_EPSILON) * DECIMAL_SCALE) / DECIMAL_SCALE
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    private fun replacePetVariables(line: String, replacements: Map<String, String>): String =
        variablePattern.replace(line) { match -> replacements[match.groupValues[1]] ?: match.value }

    private data class PetIdentity(
        val name: String,
        val rarity: SkyBlockRarity,
        val maxLevel: Int,
    )

    private const val DEFAULT_MAX_LEVEL = 100
    private const val CACHE_SIZE = 512
    private const val DECIMAL_SCALE = 10.0
    private const val ROUNDING_EPSILON = 0.0000001
    private val variablePattern = Regex("\\{([^{}]+)}")
}

internal object SkyBlockPetCatalog {
    fun addTo(
        pets: Map<String, SkyBlockPetInfo>,
        maxLevels: Map<String, Int>,
        entries: MutableList<ItemListEntry>,
        info: MutableMap<ItemListEntryKey, SkyBlockItemInfo>,
        providers: MutableMap<ItemListEntryKey, () -> ItemStack>,
    ) {
        pets.forEach { (id, pet) ->
            pet.tiers.keys.forEach { tier ->
                val ingredientId = "$id;$tier"
                val key = requireNotNull(petItemKey(ingredientId)) {
                    "Item List pet $ingredientId has an invalid rarity"
                }
                val rarity = requireNotNull(SkyBlockRarity.getByName(tier))
                val tooltip = requireNotNull(SkyBlockPetStacks.tooltip(ingredientId, 1, pets, maxLevels)) {
                    "Item List pet $ingredientId has no tooltip"
                }
                val displayName = "${pet.name} Pet"
                entries += ItemListEntry(
                    key = key,
                    displayName = displayName,
                    source = CatalogSources.SKYBLOCK,
                    searchableText = itemListSearchableText(displayName, key.id, tooltip.lore + tier + "pet"),
                    formattedDisplayName = "${rarity.chatColorCode}$displayName",
                )
                info[key] = SkyBlockItemInfo(
                    key = key,
                    displayName = displayName,
                    source = CatalogSources.SKYBLOCK,
                    category = "PET",
                    rarity = tier,
                    lore = tooltip.lore,
                )
                providers[key] = {
                    requireNotNull(SkyBlockPetStacks.stack(ingredientId, 1, pets, maxLevels)) {
                        "Item List pet $ingredientId has no display stack"
                    }
                }
            }
        }
    }
}

internal fun petItemKey(ingredientId: String): ItemListEntryKey? {
    val separator = ingredientId.lastIndexOf(';')
    if (separator <= 0 || separator == ingredientId.lastIndex) return null
    val rarity = SkyBlockRarity.getByName(ingredientId.substring(separator + 1)) ?: return null
    return ItemListEntryKey(ItemListEntryKind.SKYBLOCK, "${ingredientId.substring(0, separator)};${rarity.id}")
}

internal data class SkyBlockPetTooltip(
    val name: String,
    val lore: List<String>,
)
