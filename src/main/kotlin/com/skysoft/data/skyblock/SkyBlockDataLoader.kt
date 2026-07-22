package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.data.SkyBlockIsland
import com.skysoft.features.pets.PetItemStacks
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.WorldVec
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal object SkyBlockDataLoader {
    private val gson = Gson()

    fun loadBundled(): SkyBlockDataSnapshot {
        return loadJson(
            itemsJson = resourceText(CatalogResources.ITEMS),
            recipesJson = resourceText(CatalogResources.RECIPES),
            wikiJson = resourceText(CatalogResources.WIKI),
            mobsJson = resourceText(CatalogResources.MOBS),
            petsJson = resourceText(CatalogResources.PETS),
            supplementalJson = resourceText(CatalogResources.SUPPLEMENTAL),
            enchantmentsJson = resourceText(CatalogResources.ENCHANTMENTS),
            attributeShardsJson = resourceText(CatalogResources.ATTRIBUTE_SHARDS),
        )
    }

    fun loadJson(
        itemsJson: String,
        recipesJson: String,
        wikiJson: String,
        mobsJson: String,
        petsJson: String = resourceText(CatalogResources.PETS),
        supplementalJson: String = resourceText(CatalogResources.SUPPLEMENTAL),
        enchantmentsJson: String = resourceText(CatalogResources.ENCHANTMENTS),
        attributeShardsJson: String = resourceText(CatalogResources.ATTRIBUTE_SHARDS),
    ): SkyBlockDataSnapshot {
        require(itemsJson.length in CatalogLimits.MINIMUM_ITEMS_BYTES..CatalogLimits.MAXIMUM_ITEMS_BYTES) {
            "Item List item data has an invalid size"
        }
        require(recipesJson.length in CatalogLimits.MINIMUM_RECIPES_BYTES..CatalogLimits.MAXIMUM_RECIPES_BYTES) {
            "Item List recipe data has an invalid size"
        }
        require(wikiJson.length in CatalogLimits.MINIMUM_WIKI_BYTES..CatalogLimits.MAXIMUM_WIKI_BYTES) {
            "Item List wiki data has an invalid size"
        }
        require(mobsJson.length in CatalogLimits.MINIMUM_MOBS_BYTES..CatalogLimits.MAXIMUM_MOBS_BYTES) {
            "Item List entity data has an invalid size"
        }
        require(petsJson.length in CatalogLimits.MINIMUM_PETS_BYTES..CatalogLimits.MAXIMUM_PETS_BYTES) {
            "Item List pet data has an invalid size"
        }
        require(supplementalJson.length in CatalogLimits.MINIMUM_SUPPLEMENTAL_BYTES..CatalogLimits.MAXIMUM_SUPPLEMENTAL_BYTES) {
            "Item List supplemental data has an invalid size"
        }
        require(enchantmentsJson.length in CatalogLimits.MINIMUM_ENCHANTMENTS_BYTES..CatalogLimits.MAXIMUM_ENCHANTMENTS_BYTES) {
            "Item List enchantment data has an invalid size"
        }
        val items = readItems(itemsJson)
        require(items.size >= CatalogLimits.MINIMUM_ITEM_COUNT) { "Bundled Item List contains only ${items.size} items" }
        val supplemental = SkyBlockAuxiliaryDataLoader.readSupplemental(supplementalJson)
        val recipes = readRecipes(recipesJson, supplemental.progressionRequirements)
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
        val entities = readEntities(mobsJson, entityContextExceptions + generatedEntityContexts, npcAvailability)
        val pets = SkyBlockAuxiliaryDataLoader.readPets(petsJson)
        val enchantments = SkyBlockEnchantments.read(enchantmentsJson)
        val attributeShards = AttributeShardItemCatalog.read(attributeShardsJson)
        val obtainSources = SkyBlockAuxiliaryDataLoader.readObtainSources(
            resourceText(CatalogResources.OBTAIN_SOURCES),
        )
        require(enchantments.size >= CatalogLimits.MINIMUM_ENCHANTMENT_COUNT) {
            "Item List enchantment data contains only ${enchantments.size} tiers"
        }
        require(pets.size >= CatalogLimits.MINIMUM_PET_COUNT) {
            "Item List pet data contains only ${pets.size} pets"
        }
        return buildSnapshot(
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

    private fun buildSnapshot(
        items: List<SkyBlockItemJson>,
        enchantments: List<BundledEnchantment>,
        recipes: List<SkyBlockRecipe>,
        wiki: Map<ItemListEntryKey, String>,
        entityCatalog: EntityCatalog,
        pets: Map<String, SkyBlockPetInfo>,
        supplemental: SupplementalCatalog,
        obtainSources: Map<String, SkyBlockObtainInfo>,
        attributeShards: List<BundledAttributeShard>,
    ): SkyBlockDataSnapshot {
        val entries = mutableListOf<ItemListEntry>()
        val info = mutableMapOf<ItemListEntryKey, SkyBlockItemInfo>()
        val providers = mutableMapOf<ItemListEntryKey, () -> ItemStack>()
        SkyBlockItemCatalog.addTo(items, entries, info, providers)

        val resolvedWiki = wiki.toMutableMap()
        SkyBlockPetCatalog.addTo(pets, supplemental.petMaxLevels, entries, info, providers)
        val attributeShardObtainSources = addAttributeShards(attributeShards, entries, info, providers, resolvedWiki)
        SkyBlockEnchantments.addTo(
            enchantments,
            entries,
            info,
            providers,
            resolvedWiki,
        )

        RegistryItemCatalog.addTo(entries, info, providers)

        val bundledItemIds = items.mapNotNullTo(mutableSetOf(), SkyBlockItemJson::internalName).apply {
            addAll(attributeShards.map { it.item.internalName })
        }
        val resolvedObtainSources = resolveObtainSources(
            obtainSources + attributeShardObtainSources,
            bundledItemIds,
            enchantments,
            pets,
        )
        validateProgressionRequirements(
            supplemental.progressionRequirements,
            providers.keys,
            entityCatalog.entities,
        )
        addObtainWikiLinks(resolvedWiki, resolvedObtainSources)

        val soldByItem = soldByItem(recipes, entityCatalog.entities)
        info.replaceAll { key, value ->
            if (key.kind != ItemListEntryKind.SKYBLOCK) {
                value
            } else {
                value.copy(
                    droppedBy = entityCatalog.droppedByItem[key.id].orEmpty(),
                    dropSources = entityCatalog.dropSourcesByItem[key.id].orEmpty(),
                    soldBy = soldByItem[key.id].orEmpty(),
                    obtain = resolvedObtainSources[key.id],
                )
            }
        }
        val orderedEntries = entries.distinctBy(ItemListEntry::key).sortedWith(
            compareBy<ItemListEntry> { it.key.kind != ItemListEntryKind.SKYBLOCK }
                .thenBy { it.source.lowercase(Locale.ROOT) }
                .thenBy { it.displayName.lowercase(Locale.ROOT) },
        )
        val tierIndex = ItemListTierFamilies.build(orderedEntries)
        val indexedRecipes = recipes + AttributeShardItemCatalog.recipes(attributeShards)
        val byResult = indexedRecipes.groupBy { recipeKey(it.result) }
        val byIngredient = buildUsageIndex(indexedRecipes, ::recipeKeyOrNull)
        val entryKeys = orderedEntries.mapTo(mutableSetOf(), ItemListEntry::key)
        val unresolvedReferences = indexedRecipes.asSequence()
            .flatMap { recipe ->
                sequenceOf(recipe.result) + recipe.ingredients.asSequence().flatMap { it.expandedOptions() }
            }
            .mapNotNull(::recipeKeyOrNull)
            .filterNot(entryKeys::contains)
            .distinct()
            .toSet()
        require(unresolvedReferences.size <= CatalogLimits.MAX_UNRESOLVED_REFERENCES) {
            "Item List data has ${unresolvedReferences.size} unresolved item references: " +
                unresolvedReferences.take(CatalogLimits.UNRESOLVED_ERROR_LIMIT).joinToString { it.id }
        }
        return SkyBlockDataSnapshot(
            orderedEntries,
            orderedEntries.associateBy(ItemListEntry::key),
            info,
            byResult,
            byIngredient,
            resolvedWiki,
            providers,
            unresolvedReferences.size,
            entityCatalog.entities,
            pets,
            supplemental.petMaxLevels,
            supplemental.warps,
            tierIndex.families,
            tierIndex.byItem,
        )
    }

    private fun readItems(json: String): List<SkyBlockItemJson> = StringReader(json).use { reader ->
        gson.fromJson(reader, Array<SkyBlockItemJson>::class.java).orEmpty().toList()
    }

    private fun soldByItem(
        recipes: List<SkyBlockRecipe>,
        entities: Map<String, SkyBlockEntityInfo>,
    ): Map<String, List<String>> {
        val processes = recipes.asSequence().filterIsInstance<SkyBlockRecipe.Process>()
        val unresolvedSources = processes.mapNotNull(SkyBlockRecipe.Process::sourceId)
            .filterNot(entities::containsKey)
            .distinct()
            .toList()
        require(unresolvedSources.isEmpty()) {
            "Item List entity data is missing recipe sources: ${unresolvedSources.joinToString()}"
        }
        return recipes.asSequence()
            .filterIsInstance<SkyBlockRecipe.Process>()
            .filter { it.type == SkyBlockRecipeType.SHOP && it.sourceId != null }
            .groupBy({ it.result.id }, { requireNotNull(it.sourceId) })
            .mapValues { (_, sources) -> sources.distinct() }
    }

    private fun readRecipes(
        json: String,
        progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    ): List<SkyBlockRecipe> = StringReader(json).use { reader ->
        JsonParser.parseReader(reader).asJsonArray.mapNotNull { parseRecipe(it.asJsonObject, progressionRequirements) }
    }

    private fun parseRecipe(
        json: JsonObject,
        progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    ): SkyBlockRecipe? = when (json.string("type")) {
        "crafting" -> parseCrafting(json, progressionRequirements)
        "forge" -> parseProcess(json, SkyBlockRecipeType.FORGE)
        "kat" -> parseKat(json)
        "shop" -> parseShop(json)
        else -> null
    }

    private fun parseCrafting(
        json: JsonObject,
        progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    ): SkyBlockRecipe.Crafting? {
        val result = json.obj("result")?.recipeIngredient() ?: return null
        val keys = json.array("keys")?.mapNotNull { it.asJsonObject.recipeIngredient() }.orEmpty()
        val slots = json.array("pattern")?.map { element ->
            element.asInt.takeIf { it >= 0 }?.let(keys::getOrNull)
        }.orEmpty()
        if (slots.isEmpty()) return null
        return SkyBlockRecipe.Crafting(
            result,
            slots.take(CRAFTING_SLOT_COUNT).padTo(CRAFTING_SLOT_COUNT),
            progressionRequirements[result.id],
        )
    }

    private fun parseProcess(json: JsonObject, type: SkyBlockRecipeType): SkyBlockRecipe.Process? {
        val result = json.obj("result")?.recipeIngredient() ?: return null
        val inputs = json.array("inputs")?.mapNotNull { it.asJsonObject.recipeIngredient() }.orEmpty()
        return SkyBlockRecipe.Process(
            type = type,
            result = result,
            ingredients = inputs,
            coins = json.long("coins"),
            durationSeconds = json.long("time"),
        )
    }

    private fun parseKat(json: JsonObject): SkyBlockRecipe.Process? {
        val result = json.obj("output")?.recipeIngredient() ?: return null
        val input = json.obj("input")?.recipeIngredient()
        val ingredients = buildList {
            input?.let(::add)
            json.array("items")?.mapNotNullTo(this) { it.asJsonObject.recipeIngredient() }
        }
        return SkyBlockRecipe.Process(
            type = SkyBlockRecipeType.KAT,
            result = result,
            ingredients = ingredients,
            coins = json.long("coins"),
            durationSeconds = json.long("time"),
            sourceId = KAT_ENTITY_ID,
        )
    }

    private fun parseShop(json: JsonObject): SkyBlockRecipe.Process? {
        val result = json.obj("result")?.recipeIngredient() ?: return null
        val inputs = json.array("inputs")?.mapNotNull { it.asJsonObject.recipeIngredient() }.orEmpty()
        return SkyBlockRecipe.Process(
            type = SkyBlockRecipeType.SHOP,
            result = result,
            ingredients = inputs,
            sourceId = json.string("npc").takeIf(String::isNotBlank),
        )
    }

    private fun readEntities(
        json: String,
        entityContexts: Map<String, List<String>>,
        npcAvailability: Map<String, SkyBlockNpcAvailability>,
    ): EntityCatalog {
        val entities = mutableMapOf<String, SkyBlockEntityInfo>()
        val droppedByItem = mutableMapOf<String, MutableList<String>>()
        val dropSourcesByItem = mutableMapOf<String, MutableList<SkyBlockDropSource>>()
        JsonParser.parseString(json).asJsonObject.entrySet().forEach { (id, element) ->
            if (!element.isJsonObject) return@forEach
            val value = element.asJsonObject
            val name = value.string("name").takeIf(String::isNotBlank) ?: return@forEach
            val contexts = entityContexts[id].orEmpty()
            val island = value.string("island").takeIf(String::isNotBlank)
                ?.let { SkyBlockIsland.getByLocation(it, null) }
                ?: contexts.firstNotNullOfOrNull(::entityContextIsland)
            val position = value.obj("position")?.let { position ->
                WorldVec(position.coordinateValue("x"), position.coordinateValue("y"), position.coordinateValue("z"))
            }
            entities[id] = SkyBlockEntityInfo(
                id = id,
                name = name.removeColor(),
                type = value.string("type").ifBlank { "Entity" },
                location = entityLocation(value) ?: contexts.firstOrNull(),
                texture = value.string("texture").takeIf(String::isNotBlank),
                itemId = value.string("itemId").takeIf(String::isNotBlank),
                island = island,
                position = position,
                details = contexts,
                availability = npcAvailability[id],
            )
            value.array("lootTables")?.forEach { table ->
                val tableJson = table.asJsonObject
                val sourceName = tableJson.string("name").removeColor().takeIf(String::isNotBlank)
                tableJson.array("drops")?.forEach { drop ->
                    val dropJson = drop.asJsonObject
                    val itemId = dropJson.dropItemId() ?: return@forEach
                    droppedByItem.getOrPut(itemId) { mutableListOf() }.add(id)
                    val chance = dropJson.get("chance")?.takeUnless { it.isJsonNull }?.asDouble
                    require(chance == null || chance.isFinite() && chance in 0.0..MAXIMUM_DROP_CHANCE) {
                        "Item List entity $id has an invalid drop chance for $itemId"
                    }
                    val details = dropJson.array("extraLore")?.map { detail ->
                        detail.asString.removeColor().trim()
                    }.orEmpty().filter(String::isNotBlank)
                    require(details.all { it.length <= MAXIMUM_DROP_DETAIL_LENGTH }) {
                        "Item List entity $id has an invalid drop detail for $itemId"
                    }
                    dropSourcesByItem.getOrPut(itemId) { mutableListOf() }.add(
                        SkyBlockDropSource(id, chance, sourceName, details),
                    )
                }
            }
        }
        require(entities.size >= CatalogLimits.MINIMUM_ENTITY_COUNT) {
            "Item List entity data contains only ${entities.size} entities"
        }
        require(droppedByItem.size >= CatalogLimits.MINIMUM_DROPPED_ITEM_COUNT) {
            "Item List entity data contains only ${droppedByItem.size} dropped items"
        }
        require(npcAvailability.keys.all(entities::containsKey)) {
            "Item List NPC availability references unknown entities: " +
                npcAvailability.keys.filterNot(entities::containsKey).joinToString()
        }
        require(entityContexts.keys.all(entities::containsKey)) {
            "Item List entity contexts reference unknown entities: " +
                entityContexts.keys.filterNot(entities::containsKey).joinToString()
        }
        return EntityCatalog(
            entities = entities,
            droppedByItem = droppedByItem.mapValues { (_, ids) -> ids.distinct() },
            dropSourcesByItem = dropSourcesByItem.mapValues { (_, sources) -> sources.distinct() },
        )
    }

    private fun entityLocation(json: JsonObject): String? {
        val islandId = json.string("island").takeIf(String::isNotBlank) ?: return null
        val island = SkyBlockIsland.getByLocation(islandId, null)?.displayName
            ?: islandId.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
        val position = json.obj("position") ?: return island
        val coordinates = listOf("x", "y", "z").map { position.coordinate(it) }
        return "$island (${coordinates.joinToString()})"
    }

    private fun readWikiLinks(json: String): Map<ItemListEntryKey, String> = StringReader(json).use { reader ->
        JsonParser.parseReader(reader).asJsonArray.mapNotNull { element ->
            val json = element.asJsonObject
            val id = json.string("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val key = when (json.string("type")) {
                "item" -> ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id)
                "pet" -> petItemKey("$id;${json.string("tier")}")
                else -> null
            } ?: return@mapNotNull null
            val url = json.obj("wiki")?.string("independent")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            key to url
        }.toMap()
    }

    private fun JsonObject.recipeIngredient(): RecipeIngredient? {
        val type = string("type")
        return when (type) {
            "currency" -> RecipeIngredient(
                id = string("currency"),
                count = long("count"),
                kind = RecipeIngredientKind.CURRENCY,
                displayName = string("currency").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            )
            "pet" -> {
                val pet = string("pet")
                val tier = string("tier")
                RecipeIngredient("$pet;$tier", long("count"), RecipeIngredientKind.PET, "$tier $pet")
            }
            "enchantment" -> RecipeIngredient(
                id = enchantmentItemId(
                    string("id"),
                    get("level")?.takeUnless { it.isJsonNull }?.asInt
                        ?: error("Item List enchantment recipe is missing a level"),
                ),
                count = long("count").coerceAtLeast(1L),
                kind = RecipeIngredientKind.ITEM,
            )
            "attribute", "potion" -> RecipeIngredient(
                id = string("id"),
                count = long("count").coerceAtLeast(1L),
                kind = RecipeIngredientKind.SPECIAL,
                displayName = string("id").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            )
            else -> itemIngredient()
        }
    }

    private fun JsonObject.dropItemId(): String? = when (string("type")) {
        "enchantment" -> enchantmentItemId(
            string("id"),
            get("level")?.takeUnless { it.isJsonNull }?.asInt ?: return null,
        )
        "attribute" -> string("id").takeIf(String::isNotBlank)?.let { "ATTRIBUTE_SHARD_$it;1" }
        else -> string("id").takeIf(String::isNotBlank)
    }

    private fun JsonObject.itemIngredient(): RecipeIngredient? {
        val id = string("id").takeIf(String::isNotBlank) ?: return null
        return RecipeIngredient(id, long("count").coerceAtLeast(1L))
    }

    private fun recipeKey(ingredient: RecipeIngredient): ItemListEntryKey =
        recipeKeyOrNull(ingredient) ?: ItemListEntryKey(ItemListEntryKind.SKYBLOCK, ingredient.id)

    private fun recipeKeyOrNull(ingredient: RecipeIngredient): ItemListEntryKey? =
        when (ingredient.kind) {
            RecipeIngredientKind.ITEM -> ItemListEntryKey(ItemListEntryKind.SKYBLOCK, ingredient.id)
            RecipeIngredientKind.REGISTRY_ITEM -> ItemListEntryKey(ItemListEntryKind.REGISTRY, ingredient.id)
            RecipeIngredientKind.PET -> petItemKey(ingredient.id)
            RecipeIngredientKind.CURRENCY,
            RecipeIngredientKind.ESSENCE,
            RecipeIngredientKind.SPECIAL,
            RecipeIngredientKind.POTION,
            -> null
        }

    private fun resourceText(path: String): String =
        requireNotNull(SkyBlockDataLoader::class.java.getResourceAsStream(path)) {
            "Missing bundled Item List resource $path"
        }.bufferedReader().use { it.readText() }

    private const val CRAFTING_SLOT_COUNT = 9
    private const val KAT_ENTITY_ID = "KAT_NPC"
    private const val MAXIMUM_DROP_DETAIL_LENGTH = 160
    private const val MAXIMUM_DROP_CHANCE = 100.0
}

private object RegistryItemCatalog {
    fun addTo(
        entries: MutableList<ItemListEntry>,
        info: MutableMap<ItemListEntryKey, SkyBlockItemInfo>,
        providers: MutableMap<ItemListEntryKey, () -> ItemStack>,
    ) {
        BuiltInRegistries.ITEM.entrySet().forEach { entry ->
            val id = entry.key.identifier().toString()
            val item = entry.value
            if (item == Items.AIR) return@forEach
            val namespace = Identifier.tryParse(id)?.namespace ?: return@forEach
            if (namespace != CatalogSources.MINECRAFT) return@forEach
            val key = ItemListEntryKey(ItemListEntryKind.REGISTRY, id)
            val displayName = Component.translatable(item.descriptionId).string
            val tags = runCatching {
                BuiltInRegistries.ITEM.wrapAsHolder(item).tags().map { it.location().toString() }.toList().toSet()
            }.getOrDefault(emptySet())
            entries += ItemListEntry(
                key = key,
                displayName = displayName,
                source = namespace,
                searchableText = itemListSearchableText(displayName, id, emptyList()),
                tags = tags,
            )
            info[key] = SkyBlockItemInfo(key, displayName, CatalogSources.MINECRAFT)
            providers[key] = { ItemStack(item) }
        }
    }
}

private object CatalogResources {
    const val ITEMS = "/assets/skysoft/data/item_list/items.json"
    const val RECIPES = "/assets/skysoft/data/item_list/recipes.json"
    const val WIKI = "/assets/skysoft/data/item_list/wiki.json"
    const val MOBS = "/assets/skysoft/data/item_list/mobs.json"
    const val PETS = "/assets/skysoft/data/item_list/pets.json"
    const val SUPPLEMENTAL = "/assets/skysoft/data/item_list/supplemental.json"
    const val ENCHANTMENTS = "/assets/skysoft/data/item_list/enchantments.json"
    const val NPC_AVAILABILITY = "/assets/skysoft/data/item_list/npc_availability.json"
    const val ENTITY_CONTEXTS = "/assets/skysoft/data/item_list/entity_contexts.json"
    const val ENTITY_CONTEXT_EXCEPTIONS = "/assets/skysoft/data/item_list/entity_context_exceptions.json"
    const val OBTAIN_SOURCES = "/assets/skysoft/data/item_list/obtain_sources.json"
    const val ATTRIBUTE_SHARDS = "/assets/skysoft/data/item_list/attribute_shards.json"
}

private fun addAttributeShards(
    attributeShards: List<BundledAttributeShard>,
    entries: MutableList<ItemListEntry>,
    info: MutableMap<ItemListEntryKey, SkyBlockItemInfo>,
    providers: MutableMap<ItemListEntryKey, () -> ItemStack>,
    wiki: MutableMap<ItemListEntryKey, String>,
): Map<String, SkyBlockObtainInfo> = attributeShards.associate { shard ->
    val item = shard.item
    val key = ItemListEntryKey(ItemListEntryKind.SKYBLOCK, item.internalName)
    if (key !in info) {
        val formattedDisplayName = item.displayName ?: item.internalName
        val displayName = formattedDisplayName.removeColor()
        val searchTerms = item.lore + listOf(
            shard.attributeName,
            shard.shardName,
            shard.effect,
            shard.family,
            shard.skill,
            shard.category,
        ) + shard.hunting
        entries += ItemListEntry(
            key = key,
            displayName = displayName,
            source = CatalogSources.SKYBLOCK,
            searchableText = itemListSearchableText(displayName, item.internalName, searchTerms),
            formattedDisplayName = formattedDisplayName,
        )
        info[key] = SkyBlockItemInfo(
            key = key,
            displayName = displayName,
            source = CatalogSources.SKYBLOCK,
            category = "ATTRIBUTE SHARD",
            rarity = item.lore.lastOrNull { it.isNotBlank() }?.removeColor(),
            lore = item.lore,
        )
        providers[key] = { PetItemStacks.fromNeuItem(item) }
    }
    wiki.putIfAbsent(
        key,
        "$SKYBLOCK_WIKI_PAGE_URL${shard.wikiPage.replace(' ', '_')}",
    )
    item.internalName to SkyBlockObtainInfo(
        status = SkyBlockObtainStatus.OBTAINABLE,
        summary = shard.hunting.joinToString("; "),
        page = shard.wikiPage,
        revision = shard.wikiRevision,
        source = SkyBlockObtainSource.INDEPENDENT_WIKI,
    )
}

internal fun itemListSearchableText(displayName: String, id: String, lore: List<String>): String =
    buildString {
        append(displayName).append(' ').append(id).append(' ')
        lore.forEach { append(it.removeColor()).append(' ') }
    }.lowercase(Locale.ROOT)

internal fun enchantmentItemId(id: String, level: Int): String {
    require(id.isNotBlank()) { "Item List enchantment recipe is missing an ID" }
    require(level > 0) { "Item List enchantment recipe has an invalid level" }
    return "ENCHANTMENT_${id.uppercase(Locale.ROOT)}_$level"
}

private fun resolveObtainSources(
    obtainSources: Map<String, SkyBlockObtainInfo>,
    bundledItemIds: Set<String>,
    enchantments: List<BundledEnchantment>,
    pets: Map<String, SkyBlockPetInfo>,
): Map<String, SkyBlockObtainInfo> {
    val catalogItemIds = bundledItemIds + enchantments.map(BundledEnchantment::id) +
        pets.flatMap { (id, pet) -> pet.tiers.keys.mapNotNull { tier -> petItemKey("$id;$tier")?.id } }
    validateObtainSources(obtainSources, bundledItemIds, catalogItemIds)
    return obtainSources
}

private fun validateObtainSources(
    obtainSources: Map<String, SkyBlockObtainInfo>,
    bundledItemIds: Set<String>,
    catalogItemIds: Set<String>,
) {
    require(obtainSources.keys == catalogItemIds) {
        val missing = catalogItemIds - obtainSources.keys
        val unknown = obtainSources.keys - catalogItemIds
        "Item List obtain coverage mismatch: " +
            "missing=${missing.take(CatalogLimits.VALIDATION_SAMPLE_SIZE)}, " +
            "unknown=${unknown.take(CatalogLimits.VALIDATION_SAMPLE_SIZE)}"
    }
    val invalidSourceItems = obtainSources.values.mapNotNull(SkyBlockObtainInfo::sourceItemId)
        .filterNot(bundledItemIds::contains)
        .distinct()
    require(invalidSourceItems.isEmpty()) {
        "Item List obtain data references unknown source items: " +
            invalidSourceItems.take(CatalogLimits.VALIDATION_SAMPLE_SIZE).joinToString()
    }
}

private fun validateProgressionRequirements(
    requirements: Map<String, SkyBlockProgressionRequirement>,
    stackProviderKeys: Set<ItemListEntryKey>,
    entities: Map<String, SkyBlockEntityInfo>,
) {
    val skyBlockItemIds = stackProviderKeys.asSequence()
        .filter { it.kind == ItemListEntryKind.SKYBLOCK }
        .map(ItemListEntryKey::id)
        .toSet()
    val unknownItems = requirements.keys - skyBlockItemIds
    require(unknownItems.isEmpty()) {
        "Item List progression data references unknown items: " +
            unknownItems.take(CatalogLimits.VALIDATION_SAMPLE_SIZE).joinToString()
    }
    val invalidIcons = requirements.values.filterNot { requirement ->
        when (requirement.iconKind) {
            SkyBlockProgressionIconKind.ITEM ->
                ItemListEntryKey(ItemListEntryKind.SKYBLOCK, requirement.iconId) in stackProviderKeys
            SkyBlockProgressionIconKind.ENTITY -> entities[requirement.iconId]?.let { entity ->
                entity.texture != null || entity.itemId != null
            } == true
        }
    }
    require(invalidIcons.isEmpty()) {
        "Item List progression data has unresolved icons: " +
            invalidIcons.take(CatalogLimits.VALIDATION_SAMPLE_SIZE).joinToString { it.iconId }
    }
}

private fun addObtainWikiLinks(
    wiki: MutableMap<ItemListEntryKey, String>,
    obtainSources: Map<String, SkyBlockObtainInfo>,
) {
    obtainSources.forEach { (id, obtain) ->
        obtain.context?.let { context ->
            require(context.source == SkyBlockObtainSource.INDEPENDENT_WIKI) {
                "Unsupported Item List obtain context source ${context.source}"
            }
            wiki.putIfAbsent(ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id), context.url)
        }
    }
    obtainSources.forEach { (id, obtain) ->
        if (obtain.source != SkyBlockObtainSource.INDEPENDENT_WIKI || obtain.page.isBlank()) return@forEach
        val page = obtain.page.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment.replace(' ', '_'), StandardCharsets.UTF_8).replace("+", "%20")
        }
        wiki.putIfAbsent(
            ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id),
            "$SKYBLOCK_WIKI_PAGE_URL$page",
        )
    }
}

private data class EntityCatalog(
    val entities: Map<String, SkyBlockEntityInfo>,
    val droppedByItem: Map<String, List<String>>,
    val dropSourcesByItem: Map<String, List<SkyBlockDropSource>>,
)

private fun entityContextIsland(context: String): SkyBlockIsland? {
    val location = context.substringBefore(" >").trim()
    return SkyBlockIsland.getByLocation(location, location)
}

private object CatalogLimits {
    const val MINIMUM_ITEM_COUNT = 5_000
    const val MINIMUM_RECIPE_COUNT = 3_000
    const val MAX_UNRESOLVED_REFERENCES = 5
    const val UNRESOLVED_ERROR_LIMIT = 10
    const val MINIMUM_ITEMS_BYTES = 1_000_000
    const val MAXIMUM_ITEMS_BYTES = 32_000_000
    const val MINIMUM_RECIPES_BYTES = 100_000
    const val MAXIMUM_RECIPES_BYTES = 8_000_000
    const val MINIMUM_WIKI_BYTES = 100_000
    const val MAXIMUM_WIKI_BYTES = 8_000_000
    const val MINIMUM_MOBS_BYTES = 100_000
    const val MAXIMUM_MOBS_BYTES = 4_000_000
    const val MINIMUM_ENTITY_COUNT = 500
    const val MINIMUM_DROPPED_ITEM_COUNT = 500
    const val MINIMUM_PET_COUNT = 50
    const val MINIMUM_PETS_BYTES = 100_000
    const val MINIMUM_ENCHANTMENTS_BYTES = 20_000
    const val MAXIMUM_ENCHANTMENTS_BYTES = 1_000_000
    const val MINIMUM_ENCHANTMENT_COUNT = 500
    const val MAXIMUM_PETS_BYTES = 2_000_000
    const val MINIMUM_SUPPLEMENTAL_BYTES = 20_000
    const val MAXIMUM_SUPPLEMENTAL_BYTES = 1_000_000
    const val VALIDATION_SAMPLE_SIZE = 10
}

private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.array(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
private fun JsonObject.coordinate(name: String): String {
    val value = coordinateValue(name)
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
private fun JsonObject.coordinateValue(name: String): Double =
    get(name)?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0
private fun <T> List<T>.padTo(size: Int): List<T?> = map<T, T?> { it } + List((size - this.size).coerceAtLeast(0)) { null }
