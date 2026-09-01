package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal object AttributeShardItemCatalog {
    private val gson = Gson()
    private val catalogType = object : TypeToken<AttributeShardCatalogJson>() {}.type

    fun read(json: String): List<BundledAttributeShard> {
        require(json.length in MINIMUM_BYTES..MAXIMUM_BYTES) {
            "Item List Attribute Shard data has an invalid size"
        }
        val catalog = gson.fromJson<AttributeShardCatalogJson>(json, catalogType)
        require(catalog.schemaVersion == SCHEMA_VERSION) {
            "Item List Attribute Shard data has an unsupported schema"
        }
        require(
            catalog.sources.size >= MINIMUM_SOURCE_COUNT && catalog.sources.all { source ->
                source.name.isNotBlank() && source.url.startsWith("https://") && source.license.isNotBlank() &&
                    source.revision.isNotBlank()
            },
        ) {
            "Item List Attribute Shard data has invalid attribution"
        }
        require(catalog.items.size >= MINIMUM_COUNT) {
            "Item List Attribute Shard data contains only ${catalog.items.size} entries"
        }
        val ids = catalog.items.map { it.item.internalName }
        require(ids.toSet().size == ids.size && ids.all(attributeShardIdPattern::matches)) {
            "Item List Attribute Shard data contains invalid or duplicate IDs"
        }
        require(
            catalog.items.all { shard ->
                shard.attributeName.isNotBlank() && shard.shardName.isNotBlank() && shard.effect.isNotBlank() &&
                    shard.family.isNotBlank() && shard.skill.isNotBlank() && shard.category.isNotBlank() &&
                    shard.hunting.isNotEmpty() && shard.hunting.none(String::isBlank) &&
                    shard.wikiPage.startsWith("Attributes/List/") && shard.wikiRevision > 0L
            },
        ) {
            "Item List Attribute Shard data contains incomplete wiki metadata"
        }
        val knownIds = ids.toSet()
        require(
            catalog.items.flatMap(BundledAttributeShard::fusions).all { fusion ->
                fusion.description.startsWith("Fusing ") && fusion.ingredients.size == FUSION_INPUT_COUNT &&
                    fusion.ingredients.all { candidates ->
                        candidates.isNotEmpty() && candidates.all(knownIds::contains)
                    }
            },
        ) {
            "Item List Attribute Shard data contains invalid fusion rules"
        }
        return catalog.items
    }

    fun recipes(shards: List<BundledAttributeShard>): List<SkyBlockRecipe> {
        val byId = shards.associateBy { it.item.internalName }
        return shards.flatMap { output ->
            output.fusions.map { fusion ->
                SkyBlockRecipe.Process(
                    type = SkyBlockRecipeType.ATTRIBUTE_FUSION,
                    result = RecipeIngredient(output.item.internalName, SPECIAL_FUSION_OUTPUT_COUNT),
                    ingredients = fusion.ingredients.map { candidates -> fusionIngredient(candidates, byId) },
                )
            }
        }
    }

    private fun fusionIngredient(
        candidates: List<String>,
        shards: Map<String, BundledAttributeShard>,
    ): RecipeIngredient {
        val options = candidates.map { id -> RecipeIngredientOption(id, fusionInputCount(requireNotNull(shards[id]))) }
        val first = options.first()
        return RecipeIngredient(first.id, first.count, alternatives = options.drop(1))
    }

    private fun fusionInputCount(shard: BundledAttributeShard): Long = when {
        shard.shardName.equals("Chameleon", ignoreCase = true) -> CHAMELEON_INPUT_COUNT
        shard.family.split(',').map(String::trim).any { family -> family in TWO_SHARD_FAMILIES } ->
            FAMILY_INPUT_COUNT
        else -> DEFAULT_INPUT_COUNT
    }

    private const val SCHEMA_VERSION = 2
    private const val MINIMUM_SOURCE_COUNT = 2
    private const val MINIMUM_COUNT = 180
    private const val MINIMUM_BYTES = 200_000
    private const val MAXIMUM_BYTES = 2_000_000
    private const val FUSION_INPUT_COUNT = 2
    private const val CHAMELEON_INPUT_COUNT = 1L
    private const val FAMILY_INPUT_COUNT = 2L
    private const val DEFAULT_INPUT_COUNT = 5L
    private const val SPECIAL_FUSION_OUTPUT_COUNT = 2L
    private val TWO_SHARD_FAMILIES = setOf("Reptile", "Amphibian", "Elemental")
    private val attributeShardIdPattern = Regex("ATTRIBUTE_SHARD_[A-Z0-9_]+;1")
}

internal data class BundledAttributeShard(
    val item: NeuItemJson,
    val attributeName: String = "",
    val shardName: String = "",
    val effect: String = "",
    val hunting: List<String> = emptyList(),
    val family: String = "",
    val skill: String = "",
    val category: String = "",
    val rarity: String = "",
    val fusions: List<AttributeShardFusion> = emptyList(),
    val wikiPage: String = "",
    val wikiRevision: Long = 0L,
)

internal data class AttributeShardFusion(
    val description: String = "",
    val ingredients: List<List<String>> = emptyList(),
)

private data class AttributeShardCatalogJson(
    val schemaVersion: Int = 0,
    val sources: List<AttributeShardSourceJson> = emptyList(),
    val items: List<BundledAttributeShard> = emptyList(),
)

private data class AttributeShardSourceJson(
    val name: String = "",
    val url: String = "",
    val license: String = "",
    val revision: String = "",
)
