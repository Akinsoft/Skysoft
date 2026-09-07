package com.skysoft.data.skyblock

import com.skysoft.SkysoftMod
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.boundedAccessOrderMap
import com.skysoft.utils.net.AsyncRequestSlot
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object SkyBlockDataRepository {
    @Volatile
    var status = SkyBlockDataStatus(SkyBlockDataLoadState.NOT_LOADED)
        private set

    @Volatile
    private var catalog: LoadedCatalog? = null
    private val snapshot: SkyBlockDataSnapshot? get() = catalog?.data
    private val loadingRequest = AsyncRequestSlot<SkyBlockDataUpdater.CachedCatalog>(
        completionExecutor = Minecraft.getInstance(),
    )
    private var wasDemanded = false
    val snapshotVersion: Long
        get() = (catalog?.version ?: 0L) + MinecraftRecipeAdapter.version
    private var pricingRecipeSnapshot: SkyBlockRecipeSnapshot? = null
    @Volatile
    var updateMessage: String = "Using bundled item data"
        private set

    fun register() {
        MinecraftRecipeAdapter.register()
        SkysoftClientEvents.onClientStopping("SkyBlock data request cancellation") {
            loadingRequest.cancel()
            SkyBlockDataUpdater.cancel()
        }
        SkysoftClientEvents.onEndTick(
            "SkyBlock data demand load",
            isActive = { Demand.hasActiveConsumers || wasDemanded },
        ) {
            if (!Demand.hasActiveConsumers) {
                if (wasDemanded) {
                    loadingRequest.cancel()
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
        loadingRequest.startIfIdle(
            requestFactory = {
                CompletableFuture.supplyAsync {
                    SkyBlockDataUpdater.loadCached()
                        ?: SkyBlockDataUpdater.CachedCatalog("bundled", SkyBlockDataLoader.loadBundled())
                }
            },
        ) { loaded, error ->
            SkysoftErrorBoundary.run("Item List data load async completion") {
                if (error != null || loaded == null) {
                    status = SkyBlockDataStatus(
                        SkyBlockDataLoadState.FAILED,
                        message = error?.cause?.message ?: error?.message ?: "Unknown catalog error",
                    )
                    SkysoftMod.LOGGER.error("Skysoft Item List data failed to load", error)
                } else {
                    val isBundled = loaded.revision == "bundled"
                    installCatalog(
                        loaded.snapshot,
                        source = if (isBundled) "Bundled" else "Updated",
                        message = if (isBundled) "Using bundled item data" else "Using updated item data",
                    )
                    SkyBlockDataUpdater.check()
                }
            }
        }
    }

    val entries: List<ItemListEntry>
        get() = snapshot?.entries.orEmpty()

    internal val itemNames: SkyBlockItemNameIndex?
        get() = catalog?.itemNames

    internal val petSkinEntries: List<ItemListEntry>
        get() = catalog?.petSkinEntries.orEmpty()

    fun search(query: String): List<ItemListEntry> = catalog?.search(query).orEmpty()

    fun entry(key: ItemListEntryKey): ItemListEntry? = snapshot?.entriesByKey?.get(key)

    fun info(key: ItemListEntryKey): SkyBlockItemInfo? = snapshot?.itemInfo?.get(key)

    fun entity(id: String): SkyBlockEntityInfo? = snapshot?.entities?.get(id)

    internal object ViewerData {
        fun petStack(ingredientId: String, level: Int): ItemStack? {
            val current = snapshot ?: return null
            return current.petCatalog.stack(ingredientId, level)
        }

        fun petMaxLevel(ingredientId: String): Int =
            snapshot?.petCatalog?.maxLevel(ingredientId) ?: DEFAULT_PET_MAX_LEVEL

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
            val current = catalog ?: return emptyList()
            return ItemListTierFamilies.groupedEntries(
                current.search(query),
                TierFamilyIndex(current.data.tierFamilies, current.data.tierFamilyByItem),
                current.data.entriesByKey,
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

    fun wikiLink(key: ItemListEntryKey): String? = snapshot?.wikiLinks?.get(key)

    fun recipesFor(key: ItemListEntryKey): List<SkyBlockRecipe> =
        (snapshot?.recipesByResult?.get(key).orEmpty() + MinecraftRecipeAdapter.recipesFor(key)).distinct()

    internal val pricingRecipes: SkyBlockRecipeSnapshot?
        get() {
            val current = catalog ?: return null
            val repositorySnapshot = current.data
            val minecraftRecipes = MinecraftRecipeAdapter.recipesByResult
            val version = current.version + MinecraftRecipeAdapter.version
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
        ensureLoaded()
        return catalog?.stack(key)?.copy()
    }

    internal fun displayStack(key: ItemListEntryKey): ItemStack? = catalog?.stack(key)

    fun itemKey(internalName: String): ItemListEntryKey = ItemListEntryKey(ItemListEntryKind.SKYBLOCK, internalName)

    internal fun applyUpdated(updated: SkyBlockDataSnapshot, revision: String) {
        loadingRequest.cancel()
        installCatalog(updated, source = "Updated", message = "Item data updated (${revision.take(REVISION_DISPLAY_LENGTH)})")
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

    private fun installCatalog(updated: SkyBlockDataSnapshot, source: String, message: String) {
        catalog = LoadedCatalog(updated, (catalog?.version ?: 0L) + 1)
        pricingRecipeSnapshot = null
        status = SkyBlockDataStatus(
            state = SkyBlockDataLoadState.READY,
            source = source,
            itemCount = updated.entries.size,
            recipeCount = updated.recipesByResult.values.sumOf(List<SkyBlockRecipe>::size),
            unresolvedReferenceCount = updated.unresolvedReferenceCount,
        )
        updateMessage = message
    }

    private class LoadedCatalog(val data: SkyBlockDataSnapshot, val version: Long) {
        val itemNames by lazy { SkyBlockItemNameIndex(data) }
        val petSkinEntries by lazy {
            data.entries.filter {
                it.key.kind == ItemListEntryKind.SKYBLOCK && it.key.id.startsWith("PET_SKIN_")
            }
        }
        private val stackCache = boundedAccessOrderMap<ItemListEntryKey, ItemStack>(STACK_CACHE_SIZE)
        private val searchCache = boundedAccessOrderMap<String, List<ItemListEntry>>(SEARCH_CACHE_SIZE)

        fun search(query: String): List<ItemListEntry> {
            synchronized(searchCache) { searchCache[query]?.let { return it } }
            val result = ItemListSearch.filter(data.entries, query)
            synchronized(searchCache) { searchCache[query] = result }
            return result
        }

        fun stack(key: ItemListEntryKey): ItemStack? {
            if (key.id.startsWith(ENCHANTMENT_PREFIX)) return data.stackProviders[key]?.invoke()
            synchronized(stackCache) {
                stackCache[key]?.let { return it }
            }
            val created = data.stackProviders[key]?.invoke() ?: return null
            synchronized(stackCache) { stackCache[key] = created }
            return created
        }
    }

    private const val STACK_CACHE_SIZE = 384
    private const val SEARCH_CACHE_SIZE = 32
    private const val REVISION_DISPLAY_LENGTH = 12
    private const val DEFAULT_PET_MAX_LEVEL = 100
    private const val ENCHANTMENT_PREFIX = "ENCHANTMENT_"
}

internal fun recipeIngredientStack(ingredient: RecipeIngredient): ItemStack? =
    if (ingredient.kind == RecipeIngredientKind.POTION) MinecraftRecipeAdapter.potionStack(ingredient) else null
