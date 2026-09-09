package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.ItemListTierFamily
import com.skysoft.data.skyblock.ItemListTierFamilyKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType

internal class ItemListViewerSelection(
    currentKey: ItemListEntryKey,
    mode: ItemListViewMode,
) {
    var currentKey = currentKey
        private set
    var mode = mode
        private set
    private val backStack = mutableListOf<ViewerLocation>()
    private val forwardStack = mutableListOf<ViewerLocation>()
    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()

    var selectedType: SkyBlockRecipeType? = null
        private set
    var selectedSupplemental: ViewerSupplementalCategory? = null
        private set
    var recipePage = 0
        private set

    fun clampPage(pageCount: Int) {
        recipePage = recipePage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    fun navigateTo(key: ItemListEntryKey): ViewerInputResult {
        if (key == currentKey && mode == ItemListViewMode.INFO) return ViewerInputResult.IGNORED
        backStack += location()
        forwardStack.clear()
        open(key)
        return ViewerInputResult.HANDLED
    }

    fun navigateBack(): ViewerInputResult {
        val destination = backStack.removeLastOrNull() ?: return ViewerInputResult.IGNORED
        forwardStack += location()
        restore(destination)
        return ViewerInputResult.HANDLED
    }

    fun navigateForward(): ViewerInputResult {
        val destination = forwardStack.removeLastOrNull() ?: return ViewerInputResult.IGNORED
        backStack += location()
        restore(destination)
        return ViewerInputResult.HANDLED
    }

    fun navigateMinionTier(delta: Int): ViewerInputResult {
        val family = minionFamily(currentKey) ?: return ViewerInputResult.IGNORED
        val currentIndex = family.tiers.indexOf(currentKey)
        val nextKey = family.tiers.getOrNull(currentIndex + delta) ?: return ViewerInputResult.IGNORED
        backStack += location()
        forwardStack.clear()
        open(nextKey)
        return ViewerInputResult.HANDLED
    }

    fun hasObtainMethods(): Boolean = hasObtainMethods(currentKey)

    fun hasUsages(): Boolean = SkyBlockDataRepository.usagesFor(currentKey).isNotEmpty()

    fun ensureAvailableMode() {
        val available = availableViewerMode(mode, hasObtainMethods(), hasUsages())
        if (available != mode) changeMode(available)
    }

    fun currentRecipes(): List<SkyBlockRecipe> = when (mode) {
        ItemListViewMode.RECIPES -> SkyBlockDataRepository.recipesFor(currentKey)
        ItemListViewMode.USAGES -> SkyBlockDataRepository.usagesFor(currentKey)
        ItemListViewMode.INFO -> emptyList()
    }

    fun selectedRecipes(recipes: List<SkyBlockRecipe>): List<SkyBlockRecipe> =
        if (selectedSupplemental != null) emptyList() else selectedType?.let { type ->
            recipes.filter { it.type == type }
        } ?: recipes

    fun currentCategories(): List<ViewerCategory> = buildList {
        currentRecipes().map(SkyBlockRecipe::type).distinct().forEach { add(ViewerCategory(recipeType = it)) }
        if (mode == ItemListViewMode.RECIPES && hasAuctionHouseObtainSource(currentKey)) {
            add(ViewerCategory(supplemental = ViewerSupplementalCategory.AUCTION_HOUSE))
        }
        if (mode == ItemListViewMode.RECIPES && hasObtainSourcesMatching(currentKey, SPECIALIZED_DETAIL_MARKERS)) {
            add(ViewerCategory(supplemental = ViewerSupplementalCategory.HUNTING))
        }
        if (mode == ItemListViewMode.RECIPES && hasOtherObtainSources(currentKey)) {
            add(ViewerCategory(supplemental = ViewerSupplementalCategory.SOURCES))
        }
        if (mode == ItemListViewMode.RECIPES && hasBazaarObtainSource(currentKey)) {
            add(ViewerCategory(supplemental = ViewerSupplementalCategory.BAZAAR))
        }
    }

    fun ensureSelectedCategory(categories: List<ViewerCategory>) {
        if (categories.any(::isSelected)) return
        selectedType = categories.firstOrNull()?.recipeType
        selectedSupplemental = categories.firstOrNull()?.supplemental
        recipePage = 0
    }

    fun isSelected(category: ViewerCategory): Boolean =
        category.recipeType == selectedType && category.supplemental == selectedSupplemental

    fun selectCategory(category: ViewerCategory): ViewerInputResult {
        if (isSelected(category)) return ViewerInputResult.IGNORED
        selectedType = category.recipeType
        selectedSupplemental = category.supplemental
        recipePage = 0
        return ViewerInputResult.HANDLED
    }

    fun changeMode(requestedMode: ItemListViewMode): ViewerInputResult {
        val nextMode = availableViewerMode(requestedMode, hasObtainMethods(), hasUsages())
        if (mode == nextMode || nextMode != requestedMode) return ViewerInputResult.IGNORED
        mode = nextMode
        selectedType = null
        selectedSupplemental = null
        recipePage = 0
        return ViewerInputResult.HANDLED
    }

    fun changePage(
        delta: Int,
        pageSize: Int,
        changeAuctionPage: (Int) -> ViewerInputResult,
    ): ViewerInputResult = when {
        mode == ItemListViewMode.RECIPES && currentKey.kind == ItemListEntryKind.ENTITY -> {
            val dropCount = SkyBlockDataRepository.entity(currentKey.id)?.let(::entityDropCount) ?: 0
            changePageWithin(dropCount, pageSize, delta)
        }
        mode == ItemListViewMode.RECIPES && selectedSupplemental == ViewerSupplementalCategory.AUCTION_HOUSE ->
            changeAuctionPage(delta)
        mode == ItemListViewMode.INFO || selectedSupplemental != null -> ViewerInputResult.IGNORED
        else -> changePageWithin(selectedRecipes(currentRecipes()).size, pageSize, delta)
    }

    private fun changePageWithin(entryCount: Int, pageSize: Int, delta: Int): ViewerInputResult {
        val pageCount = recipePageCount(entryCount, pageSize)
        val nextPage = (recipePage + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (nextPage == recipePage) return ViewerInputResult.IGNORED
        recipePage = nextPage
        return ViewerInputResult.page(delta)
    }

    private fun open(key: ItemListEntryKey) {
        currentKey = key
        mode = ItemListViewMode.INFO
        selectedType = null
        selectedSupplemental = null
        recipePage = 0
    }

    private fun location(): ViewerLocation = ViewerLocation(currentKey, mode, selectedType, selectedSupplemental, recipePage)

    private fun restore(location: ViewerLocation) {
        currentKey = location.key
        mode = availableViewerMode(location.mode, hasObtainMethods(), hasUsages())
        selectedType = location.type
        selectedSupplemental = location.supplemental
        recipePage = location.recipePage
    }
}

private data class ViewerLocation(
    val key: ItemListEntryKey,
    val mode: ItemListViewMode,
    val type: SkyBlockRecipeType?,
    val supplemental: ViewerSupplementalCategory?,
    val recipePage: Int,
)

internal data class ViewerCategory(
    val recipeType: SkyBlockRecipeType? = null,
    val supplemental: ViewerSupplementalCategory? = null,
) {
    val label: String get() = recipeType?.displayName ?: requireNotNull(supplemental).label
}

internal enum class ViewerSupplementalCategory(val label: String) {
    AUCTION_HOUSE("Auction House"),
    SOURCES("Sources"),
    HUNTING("Hunting"),
    BAZAAR("Bazaar"),
}

private fun hasObtainMethods(key: ItemListEntryKey): Boolean {
    val hasEntityDrops = key.kind == ItemListEntryKind.ENTITY &&
        SkyBlockDataRepository.entity(key.id)?.lootTables?.any { it.drops.isNotEmpty() } == true
    return hasEntityDrops ||
        SkyBlockDataRepository.recipesFor(key).isNotEmpty() ||
        hasOtherObtainSources(key) ||
        hasObtainSourcesMatching(key, SPECIALIZED_DETAIL_MARKERS) ||
        hasAuctionHouseObtainSource(key) ||
        hasBazaarObtainSource(key)
}


internal fun minionFamily(key: ItemListEntryKey): ItemListTierFamily? =
    SkyBlockDataRepository.ItemListData.tierFamily(key)?.takeIf { it.kind == ItemListTierFamilyKind.MINION }


private fun availableViewerMode(
    requested: ItemListViewMode,
    hasRecipes: Boolean,
    hasUsages: Boolean,
): ItemListViewMode = when (requested) {
    ItemListViewMode.RECIPES -> if (hasRecipes) requested else ItemListViewMode.INFO
    ItemListViewMode.USAGES -> if (hasUsages) requested else ItemListViewMode.INFO
    ItemListViewMode.INFO -> requested
}
