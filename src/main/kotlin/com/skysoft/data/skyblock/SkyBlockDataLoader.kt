package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.skysoft.data.skyblock.CatalogJson.obj
import com.skysoft.data.skyblock.CatalogJson.string
import com.skysoft.utils.BundledResources
import java.io.StringReader

internal object SkyBlockDataLoader {
    private val gson = Gson()

    fun loadBundled(): SkyBlockDataSnapshot = loadJson(
        itemsJson = resourceText(CatalogResources.ITEMS),
        recipesJson = resourceText(CatalogResources.RECIPES),
        wikiJson = resourceText(CatalogResources.WIKI),
        mobsJson = resourceText(CatalogResources.MOBS),
        npcsJson = resourceText(CatalogResources.NPCS),
        petsJson = resourceText(CatalogResources.PETS),
        mergeBundledItems = false,
    )

    fun loadJson(
        itemsJson: String,
        recipesJson: String,
        wikiJson: String,
        mobsJson: String,
        npcsJson: String,
        petsJson: String,
        supplementalJson: String = resourceText(CatalogResources.SUPPLEMENTAL),
        enchantmentsJson: String = resourceText(CatalogResources.ENCHANTMENTS),
        attributeShardsJson: String = resourceText(CatalogResources.ATTRIBUTE_SHARDS),
        mergeBundledItems: Boolean = true,
    ): SkyBlockDataSnapshot {
        requireSize(itemsJson, CatalogLimits.ITEMS_BYTES, "item")
        requireSize(recipesJson, CatalogLimits.RECIPES_BYTES, "recipe")
        requireSize(wikiJson, CatalogLimits.WIKI_BYTES, "wiki")
        requireSize(mobsJson, CatalogLimits.MOBS_BYTES, "mob")
        requireSize(npcsJson, CatalogLimits.NPCS_BYTES, "NPC")
        requireSize(petsJson, CatalogLimits.PETS_BYTES, "pet")
        requireSize(supplementalJson, CatalogLimits.SUPPLEMENTAL_BYTES, "supplemental")
        requireSize(enchantmentsJson, CatalogLimits.ENCHANTMENTS_BYTES, "enchantment")
        val loadedItems = readItems(itemsJson)
        val items = if (mergeBundledItems) mergeMissingBundledItems(loadedItems) else loadedItems
        require(items.size >= CatalogLimits.MINIMUM_ITEM_COUNT) { "Bundled Item List contains only ${items.size} items" }
        val supplemental = SkyBlockAuxiliaryDataLoader.readSupplemental(supplementalJson)
        val recipes = SkyBlockRecipeDataLoader.read(recipesJson, supplemental.progressionRequirements)
        require(recipes.size >= CatalogLimits.MINIMUM_RECIPE_COUNT) {
            "Bundled Item List contains only ${recipes.size} recipes"
        }
        val wiki = readWikiLinks(wikiJson)
        val npcAvailability = SkyBlockAuxiliaryDataLoader.readNpcAvailability(
            resourceText(CatalogResources.NPC_AVAILABILITY),
        )
        val generatedEntityContexts = SkyBlockAuxiliaryDataLoader.readEntityContexts(
            resourceText(CatalogResources.ENTITY_CONTEXTS),
        )
        val entityContextExceptions = SkyBlockAuxiliaryDataLoader.readEntityContextExceptions(
            resourceText(CatalogResources.ENTITY_CONTEXT_EXCEPTIONS),
        )
        require(generatedEntityContexts.keys.intersect(entityContextExceptions.keys).isEmpty()) {
            "Item List generated and exceptional entity contexts overlap"
        }
        val entities = SkyBlockEntityDataLoader.read(
            mobsJson,
            npcsJson,
            entityContextExceptions + generatedEntityContexts,
            npcAvailability,
            SkyBlockAuxiliaryDataLoader.readNpcLocations(resourceText(CatalogResources.NPC_LOCATIONS)),
        )
        val pets = SkyBlockAuxiliaryDataLoader.readPets(petsJson)
        val enchantments = SkyBlockEnchantments.read(enchantmentsJson)
        val attributeShards = AttributeShardItemCatalog.read(attributeShardsJson)
        val obtainSources = SkyBlockObtainDataLoader.read(
            resourceText(CatalogResources.OBTAIN_SOURCES),
        )
        require(enchantments.size >= CatalogLimits.MINIMUM_ENCHANTMENT_COUNT) {
            "Item List enchantment data contains only ${enchantments.size} tiers"
        }
        require(pets.size >= CatalogLimits.MINIMUM_PET_COUNT) {
            "Item List pet data contains only ${pets.size} pets"
        }
        return SkyBlockCatalogBuilder.build(
            items,
            enchantments,
            recipes,
            wiki,
            entities,
            pets,
            supplemental,
            obtainSources,
            attributeShards,
        )
    }

    private fun requireSize(json: String, allowedBytes: IntRange, name: String) {
        require(json.length in allowedBytes) { "Item List $name data has an invalid size" }
    }

    private fun readItems(json: String): List<SkyBlockItemJson> = StringReader(json).use { reader ->
        gson.fromJson(reader, Array<SkyBlockItemJson>::class.java).orEmpty().filterNot { item ->
            item.components.lore.any { NEU_RECIPE_PLACEHOLDER_LORE in it.text }
        }
    }

    private fun mergeMissingBundledItems(items: List<SkyBlockItemJson>): List<SkyBlockItemJson> {
        val itemIds = items.mapNotNullTo(mutableSetOf(), SkyBlockItemJson::internalName)
        val bundledItems = readItems(resourceText(CatalogResources.ITEMS))
        return items + bundledItems.filter { item -> item.internalName?.let(itemIds::add) == true }
    }

    private fun readWikiLinks(json: String): Map<ItemListEntryKey, String> = StringReader(json).use { reader ->
        JsonParser.parseReader(reader).asJsonArray.mapNotNull { element ->
            val json = element.asJsonObject
            val id = json.string("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val key = when (json.string("type")) {
                "item" -> ItemListEntryKey(ItemListEntryKind.SKYBLOCK, canonicalSkyBlockItemId(id))
                "mob" -> entityItemKey(id)
                "pet" -> petItemKey("$id;${json.string("tier")}")
                else -> null
            } ?: return@mapNotNull null
            val url = json.obj("wiki")?.string("independent")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            key to url
        }.toMap()
    }

    private fun resourceText(path: String): String =
        BundledResources.read(path, "Item List resource $path") { it.readText() }

    private const val NEU_RECIPE_PLACEHOLDER_LORE = "Used for NEU Recipes"
}

private object CatalogResources {
    const val ITEMS = "/assets/skysoft/data/item_list/items.json"
    const val RECIPES = "/assets/skysoft/data/item_list/recipes.json"
    const val WIKI = "/assets/skysoft/data/item_list/wiki.json"
    const val MOBS = "/assets/skysoft/data/item_list/mobs.json"
    const val NPCS = "/assets/skysoft/data/item_list/npcs.json"
    const val PETS = "/assets/skysoft/data/item_list/pets.json"
    const val SUPPLEMENTAL = "/assets/skysoft/data/item_list/supplemental.json"
    const val ENCHANTMENTS = "/assets/skysoft/data/item_list/enchantments.json"
    const val NPC_AVAILABILITY = "/assets/skysoft/data/item_list/npc_availability.json"
    const val NPC_LOCATIONS = "/assets/skysoft/data/item_list/npc_locations.json"
    const val ENTITY_CONTEXTS = "/assets/skysoft/data/item_list/entity_contexts.json"
    const val ENTITY_CONTEXT_EXCEPTIONS = "/assets/skysoft/data/item_list/entity_context_exceptions.json"
    const val OBTAIN_SOURCES = "/assets/skysoft/data/item_list/obtain_sources.json"
    const val ATTRIBUTE_SHARDS = "/assets/skysoft/data/item_list/attribute_shards.json"
}

private object CatalogLimits {
    const val MINIMUM_ITEM_COUNT = 5_000
    const val MINIMUM_RECIPE_COUNT = 3_000
    const val MINIMUM_PET_COUNT = 50
    const val MINIMUM_ENCHANTMENT_COUNT = 500
    val ITEMS_BYTES = 1_000_000..32_000_000
    val RECIPES_BYTES = 100_000..8_000_000
    val WIKI_BYTES = 100_000..8_000_000
    val MOBS_BYTES = 100_000..4_000_000
    val NPCS_BYTES = 100_000..2_000_000
    val PETS_BYTES = 100_000..2_000_000
    val ENCHANTMENTS_BYTES = 20_000..1_000_000
    val SUPPLEMENTAL_BYTES = 20_000..1_000_000
}
