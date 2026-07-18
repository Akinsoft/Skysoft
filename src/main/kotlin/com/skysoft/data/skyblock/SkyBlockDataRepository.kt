package com.skysoft.data.skyblock

import com.skysoft.SkysoftMod
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import net.minecraft.world.item.ItemStack

object SkyBlockDataRepository {
    @Volatile
    var status = SkyBlockDataStatus(SkyBlockDataLoadState.NOT_LOADED)
        private set

    @Volatile
    private var snapshot: SkyBlockDataSnapshot? = null
    private var loadingFuture: CompletableFuture<SkyBlockDataUpdater.CachedCatalog>? = null
    private var wasDemanded = false
    @Volatile
    var snapshotVersion = 0L
        internal set
    private var pricingRecipeSnapshot: SkyBlockRecipeSnapshot? = null
    @Volatile
    var updateMessage: String = "Using bundled item data"
        private set
    private val stackCache = object : LinkedHashMap<ItemListEntryKey, ItemStack>(STACK_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ItemListEntryKey, ItemStack>?): Boolean =
            size > STACK_CACHE_SIZE
    }
    private val searchCache = object : LinkedHashMap<String, List<ItemListEntry>>(SEARCH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ItemListEntry>>?): Boolean =
            size > SEARCH_CACHE_SIZE
    }

    fun register() {
        MinecraftRecipeAdapter.register()
        SkysoftClientEvents.onEndTick(
            "SkyBlock data demand load",
            isActive = { Demand.hasActiveConsumers || wasDemanded },
        ) {
            if (!Demand.hasActiveConsumers) {
                if (wasDemanded) {
                    loadingFuture?.cancel(true)
                    loadingFuture = null
                    if (status.state == SkyBlockDataLoadState.LOADING) {
                        status = SkyBlockDataStatus(SkyBlockDataLoadState.NOT_LOADED)
                    }
                }
                wasDemanded = false
                return@onEndTick
            }
            wasDemanded = true
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (status.state != SkyBlockDataLoadState.NOT_LOADED) return
        load()
    }

    fun reload() {
        SkyBlockDataUpdater.check(force = true)
    }

    private fun load() {
        status = SkyBlockDataStatus(SkyBlockDataLoadState.LOADING, message = "Loading item data")
        val request = CompletableFuture.supplyAsync {
            SkyBlockDataUpdater.loadCached() ?: SkyBlockDataUpdater.CachedCatalog("bundled", SkyBlockDataLoader.loadBundled())
        }
        loadingFuture = request
        request.whenComplete { loaded, error ->
            SkysoftErrorBoundary.onClientThread("Item List data load async completion") {
                if (loadingFuture !== request) return@onClientThread
                loadingFuture = null
                if (error != null || loaded == null) {
                    status = SkyBlockDataStatus(
                        SkyBlockDataLoadState.FAILED,
                        message = error?.cause?.message ?: error?.message ?: "Unknown catalog error",
                    )
                    SkysoftMod.LOGGER.error("Skysoft Item List data failed to load", error)
                } else {
                    snapshot = loaded.snapshot
                    snapshotVersion++
                    clearDerivedCaches()
                    status = SkyBlockDataStatus(
                        state = SkyBlockDataLoadState.READY,
                        source = if (loaded.revision == "bundled") "Bundled" else "Updated",
                        itemCount = loaded.snapshot.entries.size,
                        recipeCount = loaded.snapshot.recipesByResult.values.sumOf(List<SkyBlockRecipe>::size),
                        unresolvedReferenceCount = loaded.snapshot.unresolvedReferenceCount,
                    )
                    updateMessage = if (loaded.revision == "bundled") "Using bundled item data" else "Using updated item data"
                    SkyBlockDataUpdater.check()
                }
            }
        }
    }

    val entries: List<ItemListEntry>
        get() = snapshot?.entries.orEmpty()

    fun search(query: String): List<ItemListEntry> {
        synchronized(searchCache) { searchCache[query]?.let { return it } }
        val result = ItemListSearch.filter(entries, query)
        synchronized(searchCache) { searchCache[query] = result }
        return result
    }

    fun entry(key: ItemListEntryKey): ItemListEntry? = snapshot?.entriesByKey?.get(key)

    fun info(key: ItemListEntryKey): SkyBlockItemInfo? = snapshot?.itemInfo?.get(key)

    fun entity(id: String): SkyBlockEntityInfo? = snapshot?.entities?.get(id)

    internal object ViewerData {
        fun petStack(ingredientId: String, level: Int): ItemStack? {
            val current = snapshot ?: return null
            return SkyBlockPetStacks.stack(ingredientId, level, current.pets, current.petMaxLevels)
        }

        fun petMaxLevel(ingredientId: String): Int =
            snapshot?.let { SkyBlockPetStacks.maxLevel(ingredientId, it.petMaxLevels) } ?: DEFAULT_PET_MAX_LEVEL

        fun bestWarpFor(entityId: String): SkyBlockWarpPoint? {
            val current = snapshot ?: return null
            val entity = current.entities[entityId] ?: return null
            val island = entity.island ?: return null
            val candidates = current.warps.filter { it.island == island }
            val position = entity.position ?: return candidates.firstOrNull()
            return candidates.minByOrNull { it.position.distanceSq(position) }
        }
    }

    internal object ItemListData {
        fun search(query: String): List<ItemListEntry> {
            val current = snapshot ?: return emptyList()
            return ItemListTierFamilies.groupedEntries(
                SkyBlockDataRepository.search(query),
                TierFamilyIndex(current.tierFamilies, current.tierFamilyByItem),
                current.entriesByKey,
            )
        }

        fun tierFamily(key: ItemListEntryKey): ItemListTierFamily? {
            val current = snapshot ?: return null
            val familyId = current.tierFamilyByItem[key] ?: return null
            return current.tierFamilies[familyId]
        }
    }

    object Demand {
        private val consumers = ActiveConsumerRegistry()

        val hasActiveConsumers: Boolean
            get() = consumers.hasActiveConsumers

        fun register(id: String, isActive: () -> Boolean) {
            consumers.register(id, isActive)
        }
    }

    fun wikiLinks(key: ItemListEntryKey): SkyBlockWikiLinks? = snapshot?.wikiLinks?.get(key)

    fun recipesFor(key: ItemListEntryKey): List<SkyBlockRecipe> =
        (snapshot?.recipesByResult?.get(key).orEmpty() + MinecraftRecipeAdapter.recipesFor(key)).distinct()

    internal val pricingRecipes: SkyBlockRecipeSnapshot?
        get() {
            val repositorySnapshot = snapshot ?: return null
            val minecraftRecipes = MinecraftRecipeAdapter.recipesByResult
            val version = snapshotVersion
            pricingRecipeSnapshot?.takeIf { it.version == version }?.let { return it }
            val recipesByResult = if (minecraftRecipes.isEmpty()) {
                repositorySnapshot.recipesByResult
            } else {
                (repositorySnapshot.recipesByResult.keys + minecraftRecipes.keys).associateWith { key ->
                    (repositorySnapshot.recipesByResult[key].orEmpty() + minecraftRecipes[key].orEmpty()).distinct()
                }
            }
            return SkyBlockRecipeSnapshot(version, recipesByResult).also { pricingRecipeSnapshot = it }
        }

    fun usagesFor(key: ItemListEntryKey): List<SkyBlockRecipe> =
        (snapshot?.recipesByIngredient?.get(key).orEmpty() + MinecraftRecipeAdapter.usagesFor(key)).distinct()

    fun stack(key: ItemListEntryKey): ItemStack? {
        return cachedStack(key)?.copy()
    }

    internal fun displayStack(key: ItemListEntryKey): ItemStack? = cachedStack(key)

    private fun cachedStack(key: ItemListEntryKey): ItemStack? {
        if (key.id.startsWith(ENCHANTMENT_PREFIX)) return snapshot?.stackProviders?.get(key)?.invoke()
        synchronized(stackCache) {
            stackCache[key]?.let { return it }
        }
        val created = snapshot?.stackProviders?.get(key)?.invoke() ?: return null
        synchronized(stackCache) { stackCache[key] = created }
        return created
    }

    fun itemKey(internalName: String): ItemListEntryKey = ItemListEntryKey(ItemListEntryKind.SKYBLOCK, internalName)

    internal fun applyUpdated(updated: SkyBlockDataSnapshot, revision: String) {
        snapshot = updated
        snapshotVersion++
        clearDerivedCaches()
        status = SkyBlockDataStatus(
            state = SkyBlockDataLoadState.READY,
            source = "Updated",
            itemCount = updated.entries.size,
            recipeCount = updated.recipesByResult.values.sumOf(List<SkyBlockRecipe>::size),
            unresolvedReferenceCount = updated.unresolvedReferenceCount,
        )
        updateMessage = "Item data updated (${revision.take(REVISION_DISPLAY_LENGTH)})"
    }

    internal fun markUpdateChecking() {
        updateMessage = "Checking for item data updates..."
    }

    internal fun markUpdateCurrent() {
        updateMessage = "Item data is current"
    }

    internal fun markUpdateFailed(message: String) {
        updateMessage = "Item data update failed: $message"
    }

    private fun clearDerivedCaches() {
        synchronized(stackCache) { stackCache.clear() }
        synchronized(searchCache) { searchCache.clear() }
        pricingRecipeSnapshot = null
        SkyBlockEntityStacks.clear()
        SkyBlockPetStacks.clear()
    }

    private const val STACK_CACHE_SIZE = 384
    private const val SEARCH_CACHE_SIZE = 32
    private const val REVISION_DISPLAY_LENGTH = 12
    private const val DEFAULT_PET_MAX_LEVEL = 100
    private const val ENCHANTMENT_PREFIX = "ENCHANTMENT_"
}

internal fun recipeIngredientStack(ingredient: RecipeIngredient): ItemStack? =
    if (ingredient.kind == RecipeIngredientKind.POTION) MinecraftRecipeAdapter.potionStack(ingredient) else null
