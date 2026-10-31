package com.skysoft.data.skyblock

import com.skysoft.utils.boundedAccessOrderMap
import kotlin.math.floor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

internal class SkyBlockPetCatalog(
    private val pets: Map<String, SkyBlockPetInfo>,
    private val maxLevels: Map<String, Int>,
) {
    private val cache = boundedAccessOrderMap<String, ItemStack>(CACHE_SIZE)

    fun stack(
        ingredientId: String,
        level: Int,
    ): ItemStack? {
        val pet = resolve(ingredientId) ?: return null
        if (pet.tier.texture.isBlank()) return null
        val selectedLevel = level.coerceIn(1, pet.identity.maxLevel)
        val cacheKey = "$ingredientId:$selectedLevel"
        synchronized(cache) {
            cache[cacheKey]?.let { return it.copy() }
        }
        val tooltip = tooltip(pet, selectedLevel)
        val stack = SkyBlockStackFactory.texturedHead(pet.tier.texture, Component.literal(tooltip.name)).apply {
            set(
                DataComponents.LORE,
                ItemLore(tooltip.lore.map(Component::literal)),
            )
            setSkyBlockId("${pet.identity.name};${pet.identity.rarity.id}")
        }
        synchronized(cache) {
            cache[cacheKey] = stack
        }
        return stack.copy()
    }

    fun maxLevel(ingredientId: String): Int =
        petIdentity(ingredientId)?.maxLevel ?: DEFAULT_MAX_LEVEL

    fun addTo(
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
                val tooltip = requireNotNull(resolve(ingredientId)?.let { resolved -> tooltip(resolved, 1) }) {
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
                    requireNotNull(stack(ingredientId, 1)) {
                        "Item List pet $ingredientId has no display stack"
                    }
                }
            }
        }
    }

    private fun resolve(ingredientId: String): ResolvedPet? {
        val identity = petIdentity(ingredientId) ?: return null
        val info = pets[identity.name] ?: return null
        val tier = info.tiers[identity.rarity.name] ?: return null
        return ResolvedPet(identity, info, tier)
    }

    private fun tooltip(pet: ResolvedPet, level: Int): SkyBlockPetTooltip {
        val identity = pet.identity
        val selectedLevel = level.coerceIn(1, identity.maxLevel)
        val replacements = pet.tier.variables.mapValues { (_, range) ->
            formatPetVariable(range, selectedLevel, identity.maxLevel, pet.tier.variablesOffset)
        }
        return SkyBlockPetTooltip(
            name = "§7[Lvl $selectedLevel] ${identity.rarity.chatColorCode}${pet.info.name.ifBlank { identity.name }}",
            lore = pet.tier.lore.map { line -> replacePetVariables(line, replacements) },
        )
    }

    private fun petIdentity(ingredientId: String): PetIdentity? {
        val (name, rarity) = parsePetIngredient(ingredientId) ?: return null
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

    private data class ResolvedPet(
        val identity: PetIdentity,
        val info: SkyBlockPetInfo,
        val tier: SkyBlockPetTierInfo,
    )

    companion object {
        const val DEFAULT_MAX_LEVEL = 100
        private const val CACHE_SIZE = 512
        private const val DECIMAL_SCALE = 10.0
        private const val ROUNDING_EPSILON = 0.0000001
        private val variablePattern = Regex("\\{([^{}]+)}")
    }
}

internal fun petItemKey(ingredientId: String): ItemListEntryKey? {
    val (name, rarity) = parsePetIngredient(ingredientId) ?: return null
    return ItemListEntryKey(ItemListEntryKind.SKYBLOCK, "$name;${rarity.id}")
}

private fun parsePetIngredient(ingredientId: String): Pair<String, SkyBlockRarity>? {
    val separator = ingredientId.lastIndexOf(';')
    if (separator <= 0 || separator == ingredientId.lastIndex) return null
    val rarity = SkyBlockRarity.getByName(ingredientId.substring(separator + 1)) ?: return null
    return ingredientId.substring(0, separator) to rarity
}

private data class SkyBlockPetTooltip(
    val name: String,
    val lore: List<String>,
)
