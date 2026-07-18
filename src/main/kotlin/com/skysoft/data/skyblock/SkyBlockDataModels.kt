package com.skysoft.data.skyblock

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.WorldVec
import net.minecraft.world.item.ItemStack

enum class SkyBlockDataLoadState {
    NOT_LOADED,
    LOADING,
    READY,
    FAILED,
}

data class SkyBlockDataStatus(
    val state: SkyBlockDataLoadState,
    val source: String = "Bundled",
    val itemCount: Int = 0,
    val recipeCount: Int = 0,
    val unresolvedReferenceCount: Int = 0,
    val message: String? = null,
)

enum class ItemListEntryKind {
    SKYBLOCK,
    REGISTRY,
}

data class ItemListEntryKey(val kind: ItemListEntryKind, val id: String) {
    fun serialized(): String = "${kind.name.lowercase()}:$id"

    companion object {
        fun parse(value: String): ItemListEntryKey? {
            val separator = value.indexOf(':')
            if (separator <= 0 || separator == value.lastIndex) return null
            val kind = runCatching { ItemListEntryKind.valueOf(value.substring(0, separator).uppercase()) }.getOrNull()
                ?: return null
            return ItemListEntryKey(kind, value.substring(separator + 1))
        }
    }
}

data class ItemListEntry(
    val key: ItemListEntryKey,
    val displayName: String,
    val source: String,
    val searchableText: String,
    val tags: Set<String> = emptySet(),
    val formattedDisplayName: String = displayName,
)

enum class ItemListTierFamilyKind {
    MINION,
    ENCHANTMENT,
}

data class ItemListTierFamily(
    val id: String,
    val displayName: String,
    val kind: ItemListTierFamilyKind,
    val tiers: List<ItemListEntryKey>,
)

data class SkyBlockItemInfo(
    val key: ItemListEntryKey,
    val displayName: String,
    val source: String,
    val category: String? = null,
    val rarity: String? = null,
    val lore: List<String> = emptyList(),
    val flags: Set<String> = emptySet(),
    val enchantment: SkyBlockEnchantmentInfo? = null,
    val droppedBy: List<String> = emptyList(),
    val dropSources: List<SkyBlockDropSource> = emptyList(),
    val soldBy: List<String> = emptyList(),
    val obtain: SkyBlockObtainInfo? = null,
)

data class SkyBlockObtainInfo(
    val status: SkyBlockObtainStatus,
    val summary: String,
    val page: String,
    val revision: Long,
    val source: SkyBlockObtainSource,
    val sourceItemId: String? = null,
    val context: SkyBlockObtainContext? = null,
)

data class SkyBlockObtainContext(
    val label: String,
    val page: String,
    val revision: Long,
    val source: SkyBlockObtainSource,
    val url: String,
)

enum class SkyBlockObtainStatus {
    OBTAINABLE,
    UNOBTAINABLE,
    UNKNOWN,
}

enum class SkyBlockObtainSource {
    INDEPENDENT_WIKI,
    OFFICIAL_WIKI,
    SKYBLOCK_REPO,
    NEU_REPO,
    STRUCTURED_CATALOG,
    UNKNOWN,
}

data class SkyBlockEnchantmentInfo(
    val applicableOn: String,
    val applyCostLevels: Int?,
    val sources: List<SkyBlockInfoSource> = emptyList(),
)

data class SkyBlockInfoSource(
    val kind: SkyBlockInfoSourceKind,
    val displayName: String,
    val entityId: String? = null,
    val itemId: String? = null,
    val details: List<String> = emptyList(),
    val obtainStatus: SkyBlockObtainStatus? = null,
)

enum class SkyBlockInfoSourceKind {
    ENTITY,
    BAZAAR,
    EXPERIMENTATION_TABLE,
    ENCHANTMENT_TABLE,
    CATALOG,
}

data class SkyBlockDropSource(
    val entityId: String,
    val chance: Double? = null,
    val sourceName: String? = null,
    val details: List<String> = emptyList(),
)

data class SkyBlockEntityInfo(
    val id: String,
    val name: String,
    val type: String,
    val location: String?,
    val texture: String?,
    val itemId: String?,
    val island: SkyBlockIsland? = null,
    val position: WorldVec? = null,
    val details: List<String> = emptyList(),
    val availability: SkyBlockNpcAvailability? = null,
)

data class SkyBlockNpcAvailability(
    val event: SkyBlockEvent,
    val startsBeforeMinutes: Int,
    val durationMinutes: Int,
    val page: String,
    val revision: Long,
    val source: SkyBlockNpcAvailabilitySource,
)

enum class SkyBlockNpcAvailabilitySource {
    OFFICIAL_WIKI,
    NEU_REPO,
}

enum class SkyBlockProgressionKind {
    COLLECTION,
    SLAYER,
    DUNGEON_BOSS,
}

enum class SkyBlockProgressionIconKind {
    ITEM,
    ENTITY,
}

enum class SkyBlockProgressionSource {
    BUNDLED,
    INDEPENDENT_WIKI,
}

data class SkyBlockProgressionRequirement(
    val kind: SkyBlockProgressionKind,
    val name: String,
    val tier: Int,
    val iconKind: SkyBlockProgressionIconKind,
    val iconId: String,
    val source: SkyBlockProgressionSource = SkyBlockProgressionSource.BUNDLED,
) {
    val displayText: String
        get() = when (kind) {
            SkyBlockProgressionKind.COLLECTION -> "$name Collection $tier"
            SkyBlockProgressionKind.SLAYER -> "$name $tier"
            SkyBlockProgressionKind.DUNGEON_BOSS -> "$name Collection $tier"
        }

    val command: String
        get() = when (kind) {
            SkyBlockProgressionKind.COLLECTION -> "collections ${name.lowercase()}"
            SkyBlockProgressionKind.SLAYER -> "slayer"
            SkyBlockProgressionKind.DUNGEON_BOSS -> "bosscollection"
        }

    val actionTooltip: String
        get() = when (kind) {
            SkyBlockProgressionKind.COLLECTION -> "Open $name Collection"
            SkyBlockProgressionKind.SLAYER -> "Open Slayer menu"
            SkyBlockProgressionKind.DUNGEON_BOSS -> "Open Boss Collections"
        }
}

data class SkyBlockWarpPoint(
    val command: String,
    val island: SkyBlockIsland,
    val position: WorldVec,
)

internal data class SkyBlockPetInfo(
    val id: String = "",
    val name: String = "",
    val tiers: Map<String, SkyBlockPetTierInfo> = emptyMap(),
)

internal data class SkyBlockPetTierInfo(
    val texture: String = "",
    val lore: List<String> = emptyList(),
    val variables: Map<String, List<Double>> = emptyMap(),
    val variablesOffset: Int = 0,
)

data class SkyBlockWikiLinks(
    val official: String? = null,
    val independent: String? = null,
)

enum class SkyBlockRecipeType(val displayName: String) {
    ATTRIBUTE_FUSION("Fusions"),
    CRAFTING("Crafting"),
    FORGE("Forge"),
    KAT("Kat"),
    SHOP("Trade"),
    SMELTING("Smelting"),
    BLASTING("Blasting"),
    SMOKING("Smoking"),
    CAMPFIRE("Campfire"),
    STONECUTTING("Stonecutting"),
    SMITHING("Smithing"),
    BREWING("Brewing"),
    UNSUPPORTED("Unsupported"),
}

enum class RecipeIngredientKind {
    ITEM,
    REGISTRY_ITEM,
    PET,
    CURRENCY,
    ESSENCE,
    SPECIAL,
    POTION,
}

data class RecipeIngredient(
    val id: String,
    val count: Long = 1L,
    val kind: RecipeIngredientKind = RecipeIngredientKind.ITEM,
    val displayName: String? = null,
    val alternatives: List<RecipeIngredientOption> = emptyList(),
)

data class RecipeIngredientOption(
    val id: String,
    val count: Long = 1L,
)

sealed interface SkyBlockRecipe {
    val type: SkyBlockRecipeType
    val result: RecipeIngredient
    val ingredients: List<RecipeIngredient>

    data class Crafting(
        override val result: RecipeIngredient,
        val slots: List<RecipeIngredient?>,
        val progressionRequirement: SkyBlockProgressionRequirement? = null,
    ) : SkyBlockRecipe {
        override val type: SkyBlockRecipeType = SkyBlockRecipeType.CRAFTING
        override val ingredients: List<RecipeIngredient> = slots.filterNotNull()
    }

    data class Process(
        override val type: SkyBlockRecipeType,
        override val result: RecipeIngredient,
        override val ingredients: List<RecipeIngredient>,
        val coins: Long = 0L,
        val durationSeconds: Long = 0L,
        val sourceId: String? = null,
    ) : SkyBlockRecipe

}

internal data class SkyBlockDataSnapshot(
    val entries: List<ItemListEntry>,
    val entriesByKey: Map<ItemListEntryKey, ItemListEntry>,
    val itemInfo: Map<ItemListEntryKey, SkyBlockItemInfo>,
    val recipesByResult: Map<ItemListEntryKey, List<SkyBlockRecipe>>,
    val recipesByIngredient: Map<ItemListEntryKey, List<SkyBlockRecipe>>,
    val wikiLinks: Map<ItemListEntryKey, SkyBlockWikiLinks>,
    val stackProviders: Map<ItemListEntryKey, () -> ItemStack>,
    val unresolvedReferenceCount: Int,
    val entities: Map<String, SkyBlockEntityInfo> = emptyMap(),
    val pets: Map<String, SkyBlockPetInfo> = emptyMap(),
    val petMaxLevels: Map<String, Int> = emptyMap(),
    val warps: List<SkyBlockWarpPoint> = emptyList(),
    val tierFamilies: Map<String, ItemListTierFamily> = emptyMap(),
    val tierFamilyByItem: Map<ItemListEntryKey, String> = emptyMap(),
)

internal data class SkyBlockRecipeSnapshot(
    val version: Long,
    val recipesByResult: Map<ItemListEntryKey, List<SkyBlockRecipe>>,
)
