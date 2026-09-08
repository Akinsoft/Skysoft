package com.skysoft.data.skyblock

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal object SkyBlockCatalogBuilder {
    fun build(
        items: List<SkyBlockItemJson>,
        enchantments: List<BundledEnchantment>,
        recipes: List<SkyBlockRecipe>,
        wiki: Map<ItemListEntryKey, String>,
        entityCatalog: LoadedEntityCatalog,
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
        val petCatalog = SkyBlockPetCatalog(pets, supplemental.petMaxLevels)
        petCatalog.addTo(entries, info, providers)
        val attributeShardObtainSources = AttributeShardItemCatalog.addTo(attributeShards, entries, info, providers, resolvedWiki)
        SkyBlockEnchantments.addTo(
            enchantments,
            entries,
            info,
            providers,
            resolvedWiki,
        )
        SkyBlockEntityCatalog.addTo(entityCatalog.entities, entries, providers)

        RegistryItemCatalog.addTo(entries, info, providers)

        val bundledItemIds = items.mapNotNullTo(mutableSetOf(), SkyBlockItemJson::internalName).apply {
            addAll(attributeShards.map { it.item.internalName })
        }
        val resolvedObtainSources = obtainSources + attributeShardObtainSources
        validateObtainSources(resolvedObtainSources, bundledItemIds, enchantments, pets)
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
            compareBy<ItemListEntry> { it.key.kind.ordinal }
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
            .toSet()
        require(unresolvedReferences.size <= MAX_UNRESOLVED_REFERENCES) {
            "Item List data has ${unresolvedReferences.size} unresolved item references: " +
                unresolvedReferences.take(UNRESOLVED_ERROR_LIMIT).joinToString { it.id }
        }
        return SkyBlockDataSnapshot(
            entries = orderedEntries,
            entriesByKey = orderedEntries.associateBy(ItemListEntry::key),
            itemInfo = info,
            recipesByResult = byResult,
            recipesByIngredient = byIngredient,
            wikiLinks = resolvedWiki,
            stackProviders = providers,
            unresolvedReferenceCount = unresolvedReferences.size,
            petCatalog = petCatalog,
            entities = entityCatalog.entities,
            warps = supplemental.warps,
            tierFamilies = tierIndex.families,
            tierFamilyByItem = tierIndex.byItem,
        )
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

    private fun recipeKey(ingredient: RecipeIngredient): ItemListEntryKey =
        requireNotNull(ingredient.itemListKey(RecipeIngredientKeyContext.CATALOG_RESULT))

    private fun recipeKeyOrNull(ingredient: RecipeIngredient): ItemListEntryKey? =
        ingredient.itemListKey(RecipeIngredientKeyContext.CATALOG_USAGE)
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

private fun validateObtainSources(
    obtainSources: Map<String, SkyBlockObtainInfo>,
    bundledItemIds: Set<String>,
    enchantments: List<BundledEnchantment>,
    pets: Map<String, SkyBlockPetInfo>,
) {
    val catalogItemIds = bundledItemIds + enchantments.map(BundledEnchantment::id) +
        pets.flatMap { (id, pet) -> pet.tiers.keys.mapNotNull { tier -> petItemKey("$id;$tier")?.id } }
    require(obtainSources.keys == catalogItemIds) {
        val missing = catalogItemIds - obtainSources.keys
        val unknown = obtainSources.keys - catalogItemIds
        "Item List obtain coverage mismatch: " +
            "missing=${missing.take(VALIDATION_SAMPLE_SIZE)}, " +
            "unknown=${unknown.take(VALIDATION_SAMPLE_SIZE)}"
    }
    val invalidSourceItems = obtainSources.values.mapNotNull(SkyBlockObtainInfo::sourceItemId)
        .filterNot(bundledItemIds::contains)
        .distinct()
    require(invalidSourceItems.isEmpty()) {
        "Item List obtain data references unknown source items: " +
            invalidSourceItems.take(VALIDATION_SAMPLE_SIZE).joinToString()
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
            unknownItems.take(VALIDATION_SAMPLE_SIZE).joinToString()
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
            invalidIcons.take(VALIDATION_SAMPLE_SIZE).joinToString { it.iconId }
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

private const val MAX_UNRESOLVED_REFERENCES = 5
private const val UNRESOLVED_ERROR_LIMIT = 10
private const val VALIDATION_SAMPLE_SIZE = 10
