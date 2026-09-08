package com.skysoft.features.inventory.itemlist

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.RecipeIngredient
import com.skysoft.data.skyblock.RecipeIngredientKeyContext
import com.skysoft.data.skyblock.RecipeIngredientKind
import com.skysoft.data.skyblock.SkyBlockCurrencyStacks
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockEntityStacks
import com.skysoft.data.skyblock.SkyBlockProgressionIconKind
import com.skysoft.data.skyblock.SkyBlockProgressionRequirement
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType
import com.skysoft.data.skyblock.entityItemKey
import com.skysoft.data.skyblock.itemListKey
import com.skysoft.data.skyblock.recipeIngredientStack
import com.skysoft.features.inventory.crafting.addCraftingHelperTarget
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

internal class ItemListRecipeView(
    private val selection: ItemListViewerSelection,
    private val fontProvider: () -> net.minecraft.client.gui.Font,
) {
    private val font get() = fontProvider()
    private val currentKey by selection::currentKey
    private val mode by selection::mode
    private val selectedSupplemental by selection::selectedSupplemental
    private val recipePage by selection::recipePage
    private val entityDropsPanel = ItemListEntityDropsPanel()
    private val obtainSourcesPanel = ItemListObtainSourcesPanel()
    private val bazaarPanel = ItemListBazaarPanel()
    private val auctionHousePanel = ItemListAuctionHousePanel()
    private val fusionSelectorPanel = ItemListFusionSelectorPanel()
    private var ingredientBounds: List<Pair<Rect, ItemListEntryKey>> = emptyList()
    private var progressionBounds: List<Pair<Rect, SkyBlockProgressionRequirement>> = emptyList()
    private var quickCraftBounds: List<Pair<Rect, String>> = emptyList()
    private var craftingHelperBounds: List<Pair<Rect, String>> = emptyList()
    private var entityBounds: List<Pair<Rect, String>> = emptyList()
    private var petBounds: List<PetIngredientBounds> = emptyList()
    private var fusionIngredientTriggers: List<FusionIngredientTrigger> = emptyList()
    private val petLevels = mutableMapOf<RecipePetLevelKey, Int>()

    val petScrollAreas: List<Rect> get() = petBounds.map(PetIngredientBounds::bounds)

    fun entityAt(mouseX: Int, mouseY: Int): String? =
        entityBounds.firstOrNull { (bounds, _) -> bounds.contains(mouseX, mouseY) }?.second

    fun prepareFrame() {
        if (mode != ItemListViewMode.RECIPES || selectedSupplemental != null) closePopup()
        if (mode == ItemListViewMode.INFO) clearInteractionAreas()
    }

    fun closePopup(): ViewerInputResult = fusionSelectorPanel.closeSelector()

    fun changePage(delta: Int, pageSize: Int): ViewerInputResult =
        selection.changePage(delta, pageSize, auctionHousePanel::changePage)

    fun pagination(layout: ItemListViewerLayout): ItemListRecipePagination? {
        if (mode == ItemListViewMode.INFO) return null
        val entity = currentKey.takeIf { it.kind == ItemListEntryKind.ENTITY }
            ?.let { SkyBlockDataRepository.entity(it.id) }
        if (entity != null) return numberedPagination(entityDropCount(entity), layout.entityGrid.pageSize)
        if (fusionSelectorPanel.isOpen) return null
        if (selectedSupplemental == ViewerSupplementalCategory.AUCTION_HOUSE) {
            return ItemListRecipePagination(
                auctionHousePanel.canGoPrevious,
                auctionHousePanel.canGoNext,
                auctionHousePanel.pageLabel,
            )
        }
        if (selectedSupplemental != null) return null
        val recipes = selection.selectedRecipes(selection.currentRecipes())
        return numberedPagination(recipes.size, layout.recipeGrid.pageSize)
    }

    private fun numberedPagination(count: Int, pageSize: Int): ItemListRecipePagination {
        val pageCount = recipePageCount(count, pageSize)
        return ItemListRecipePagination(
            recipePage > 0,
            recipePage + 1 < pageCount,
            if (pageCount == 0) "0 / 0" else "${recipePage + 1} / $pageCount",
        )
    }

    fun click(layout: ItemListViewerLayout, mouseX: Int, mouseY: Int): ViewerInputResult {
        val categories = selection.currentCategories().take(MAX_CATEGORY_BUTTONS)
        val categoryClick = categories.withIndex().firstOrNull { (index, _) -> layout.category(index).contains(mouseX, mouseY) }
            ?.value?.let(selection::selectCategory) ?: ViewerInputResult.IGNORED
        return categoryClick.orElse {
            clickMarketPanel(layout.recipeGrid.bounds, mouseX, mouseY)
        }.orElse {
            if (mode == ItemListViewMode.RECIPES && selectedSupplemental == ViewerSupplementalCategory.HUNTING) {
                obtainSourcesPanel.clickHunting(currentKey, mouseX, mouseY)
            } else {
                ViewerInputResult.IGNORED
            }
        }.orElse {
            fusionSelectorPanel.click(fusionIngredientTriggers, mouseX, mouseY)
        }.orElse {
            sendProgressionCommandAt(progressionBounds, mouseX, mouseY)
        }.orElse {
            entityAt(mouseX, mouseY)?.let { entityId ->
                entityItemKey(entityId)
                    .takeIf { SkyBlockDataRepository.entry(it) != null }
                    ?.let(selection::navigateTo)
            } ?: ViewerInputResult.IGNORED
        }.orElse {
            navigateIngredient(mouseX, mouseY, canQuickCraft = true)
        }
    }

    private fun clickMarketPanel(bounds: Rect, mouseX: Int, mouseY: Int): ViewerInputResult =
        auctionHousePanel.click(
            mode == ItemListViewMode.RECIPES && selectedSupplemental == ViewerSupplementalCategory.AUCTION_HOUSE,
            bounds,
            mouseX,
            mouseY,
        ).orElse {
            if (mode == ItemListViewMode.RECIPES && selectedSupplemental == ViewerSupplementalCategory.BAZAAR) {
                val click = bazaarPanel.click(bounds, currentKey, mouseX, mouseY)
                if (click.isHandled) ViewerInputResult.HANDLED else ViewerInputResult.IGNORED
            } else {
                ViewerInputResult.IGNORED
            }
        }

    private fun clearInteractionAreas() {
        ingredientBounds = emptyList()
        progressionBounds = emptyList()
        quickCraftBounds = emptyList()
        craftingHelperBounds = emptyList()
        entityBounds = emptyList()
        petBounds = emptyList()
        fusionIngredientTriggers = emptyList()
    }

    fun wasPetScrollHandled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val pet = petBounds.firstOrNull { it.bounds.contains(mouseX.toInt(), mouseY.toInt()) }
        if (pet != null && scrollY != 0.0) {
            val currentLevel = petLevels[pet.levelKey] ?: 1
            val maximumLevel = SkyBlockDataRepository.ViewerData.petMaxLevel(pet.ingredientId)
            val nextLevel = (currentLevel + if (scrollY > 0.0) 1 else -1).coerceIn(1, maximumLevel)
            if (nextLevel != currentLevel) {
                petLevels[pet.levelKey] = nextLevel
                return true
            }
        }
        return false
    }

    fun render(context: GuiGraphicsExtractor, layout: ItemListViewerLayout, mouseX: Int, mouseY: Int) {
        progressionBounds = emptyList()
        quickCraftBounds = emptyList()
        craftingHelperBounds = emptyList()
        entityBounds = emptyList()
        petBounds = emptyList()
        fusionIngredientTriggers = emptyList()
        val entity = currentKey.takeIf { it.kind == ItemListEntryKind.ENTITY }
            ?.let { SkyBlockDataRepository.entity(it.id) }
        if (entity != null) {
            val dropCount = entityDropCount(entity)
            val pageCount = recipePageCount(dropCount, layout.entityGrid.pageSize)
            selection.clampPage(pageCount)
            ingredientBounds = entityDropsPanel.render(
                context,
                font,
                layout.entityGrid,
                entity,
                recipePage,
                mouseX,
                mouseY,
            )
            return
        }
        val categories = selection.currentCategories().take(MAX_CATEGORY_BUTTONS)
        selection.ensureSelectedCategory(categories)
        categories.forEachIndexed { index, category ->
            val bounds = layout.category(index)
            PixelButtonRenderer.draw(
                context,
                font,
                bounds,
                category.label,
                selection.isSelected(category),
                bounds.contains(mouseX, mouseY),
                true,
            )
        }
        when (selectedSupplemental) {
            ViewerSupplementalCategory.SOURCES -> {
                ingredientBounds = emptyList()
                entityBounds = obtainSourcesPanel.render(
                    context,
                    font,
                    layout.recipeGrid.bounds,
                    currentKey,
                    mouseX,
                    mouseY,
                )
                return
            }
            ViewerSupplementalCategory.HUNTING -> {
                ingredientBounds = emptyList()
                entityBounds = obtainSourcesPanel.render(
                    context,
                    font,
                    layout.recipeGrid.bounds,
                    currentKey,
                    mouseX,
                    mouseY,
                    SPECIALIZED_DETAIL_MARKERS,
                )
                return
            }
            ViewerSupplementalCategory.BAZAAR -> {
                ingredientBounds = emptyList()
                bazaarPanel.render(context, font, layout.recipeGrid.bounds, currentKey, mouseX, mouseY)
                return
            }
            ViewerSupplementalCategory.AUCTION_HOUSE -> {
                ingredientBounds = emptyList()
                auctionHousePanel.render(context, font, layout.recipeGrid.bounds, currentKey, mouseX, mouseY)
                return
            }
            null -> Unit
        }
        val allRecipes = selection.currentRecipes()
        val recipes = selection.selectedRecipes(allRecipes)
        val pageSize = layout.recipeGrid.pageSize
        val pageCount = recipePageCount(recipes.size, pageSize)
        selection.clampPage(pageCount)
        val visibleRecipes = recipes.drop(recipePage * pageSize).take(pageSize)
        ingredientBounds = visibleRecipes.flatMapIndexed { index, recipe ->
            val tile = layout.recipeGrid.tile(index, visibleRecipes.size)
            when (recipe) {
                is SkyBlockRecipe.Crafting -> renderCrafting(context, tile, recipe, mouseX, mouseY)
                is SkyBlockRecipe.Process -> renderProcess(context, tile, recipe, mouseX, mouseY)
            }
        }
        fusionSelectorPanel.render(context, font, layout.recipeGrid.bounds, recipes, mouseX, mouseY)
    }

    private fun renderCrafting(
        context: GuiGraphicsExtractor,
        tile: Rect,
        recipe: SkyBlockRecipe.Crafting,
        mouseX: Int,
        mouseY: Int,
    ): List<Pair<Rect, ItemListEntryKey>> {
        val crafting = ViewerCraftingLayout.create(tile, recipe.progressionRequirement != null)
        val clickable = mutableListOf<Pair<Rect, ItemListEntryKey>>()
        recipe.slots.forEachIndexed { index, ingredient ->
            val bounds = crafting.slots[index]
            drawIngredient(context, bounds, ingredient, recipe, mouseX, mouseY)?.let { clickable += bounds to it }
        }
        StringRenderable("§7->", crafting.scale.toDouble()).renderAt(context, crafting.arrow.x, crafting.arrow.y)
        drawIngredient(context, crafting.result, recipe.result, recipe, mouseX, mouseY)
            ?.let { clickable += crafting.result to it }
        itemListQuickCraftCommand(recipe.result.id)?.let { command ->
            val button = itemListQuickCraftButtonBounds(crafting.result)
            quickCraftBounds += button to command
            val isHovered = button.contains(mouseX, mouseY)
            PixelButtonRenderer.draw(context, font, button, "+", false, isHovered, true)
            if (isHovered) SkysoftNativeTooltip.setForNextFrame(context, listOf("§eQuick Craft"), mouseX, mouseY)
        }
        if (SkysoftConfigGui.config().inventory.craftingHelper.enabled) {
            val button = itemListCraftingHelperButtonBounds(crafting.result)
            craftingHelperBounds += button to recipe.result.id
            val isHovered = button.contains(mouseX, mouseY)
            PixelButtonRenderer.draw(context, font, button, "*", false, isHovered, true)
            if (isHovered) SkysoftNativeTooltip.setForNextFrame(context, listOf("§eHelp me craft"), mouseX, mouseY)
        }
        if (crafting.progressionRequirement != null && recipe.progressionRequirement != null) {
            progressionBounds += renderProgressionRequirement(
                context,
                font,
                crafting.progressionRequirement,
                recipe.progressionRequirement,
                mouseX,
                mouseY,
            )
        }
        return clickable
    }

    private fun renderProcess(
        context: GuiGraphicsExtractor,
        tile: Rect,
        recipe: SkyBlockRecipe.Process,
        mouseX: Int,
        mouseY: Int,
    ): List<Pair<Rect, ItemListEntryKey>> {
        val visibleIngredients = recipe.ingredients.take(MAX_PROCESS_INGREDIENTS)
        val process = ViewerProcessLayout.create(tile, visibleIngredients.size, recipe.sourceId != null)
        val clickable = mutableListOf<Pair<Rect, ItemListEntryKey>>()
        if (process.source != null && recipe.sourceId != null) {
            renderEntityIcon(context, font, process.source, recipe.sourceId, mouseX = mouseX, mouseY = mouseY)
            entityBounds += process.source to recipe.sourceId
        }
        visibleIngredients.forEachIndexed { index, ingredient ->
            val bounds = process.ingredients[index]
            val target = FusionIngredientTarget(recipe, index)
            val isSelectable = recipe.type == SkyBlockRecipeType.ATTRIBUTE_FUSION && ingredient.alternatives.isNotEmpty()
            val selectedIngredient = if (isSelectable) fusionSelectorPanel.selectedIngredient(target) else null
            drawIngredient(
                context,
                bounds,
                ingredient,
                recipe,
                mouseX,
                mouseY,
                selectedIngredient,
                isSelectable,
            )?.let { clickable += bounds to it }
            if (isSelectable) fusionIngredientTriggers += FusionIngredientTrigger(bounds, target)
        }
        StringRenderable("§7->", process.scale.toDouble()).renderAt(context, process.arrow.x, process.arrow.y)
        drawIngredient(context, process.result, recipe.result, recipe, mouseX, mouseY)
            ?.let { clickable += process.result to it }
        renderProcessDetails(context, font, process.details, recipe, petLevels)
        return clickable
    }

    private fun drawIngredient(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        ingredient: RecipeIngredient?,
        recipe: SkyBlockRecipe,
        mouseX: Int,
        mouseY: Int,
        displayedOverride: RecipeIngredient? = null,
        isSelectable: Boolean = false,
    ): ItemListEntryKey? {
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, ItemListSlotStyle.BORDER)
        context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, ItemListSlotStyle.FILL)
        if (ingredient == null) return null
        val displayedIngredient = displayedOverride ?: displayedRecipeIngredient(ingredient)
        val key = displayedIngredient.itemListKey(RecipeIngredientKeyContext.VIEWER)
        val petLevelKey = if (displayedIngredient.kind == RecipeIngredientKind.PET) {
            recipePetLevelKey(recipe, displayedIngredient.id)
        } else {
            null
        }
        val petLevel = petLevelKey?.let { petLevels.getOrPut(it) { 1 } }
        val currencyAmount = displayedIngredient.count.takeIf { displayedIngredient.kind == RecipeIngredientKind.CURRENCY }
        val currencyStack = currencyAmount?.let { SkyBlockCurrencyStacks.supportedStack(displayedIngredient.id, it) }
        val baseStack = when {
            petLevel != null -> SkyBlockDataRepository.ViewerData.petStack(displayedIngredient.id, petLevel)
                ?.withActionHint("SCROLL")
            currencyStack != null -> currencyStack
            else -> recipeIngredientStack(displayedIngredient) ?: key?.let(SkyBlockDataRepository::displayStack)
        }
        val stack = if (isSelectable) baseStack?.withActionHint("SELECT") else baseStack
        if (stack != null) {
            val decorationText = displayedIngredient.count.takeIf {
                it > 1 && petLevelKey == null && currencyStack == null
            }?.let(ItemListFormatting::number)
            renderViewerItem(context, font, stack, bounds, decorationText)
            when {
                petLevelKey != null ->
                    petBounds += PetIngredientBounds(bounds, displayedIngredient.id, petLevelKey)
                currencyStack != null -> drawCurrencyAmount(
                    context,
                    font,
                    bounds,
                    requireNotNull(currencyAmount),
                    requireNotNull(SkyBlockCurrencyStacks.supportedTextColor(displayedIngredient.id)),
                )
            }
            if (bounds.contains(mouseX, mouseY)) {
                if (currencyStack != null) {
                    val amount = requireNotNull(currencyAmount)
                    val color = requireNotNull(SkyBlockCurrencyStacks.supportedTextColor(displayedIngredient.id))
                    SkysoftNativeTooltip.setForNextFrame(
                        context,
                        listOf("$color${requireNotNull(SkyBlockCurrencyStacks.supportedName(ingredient.id, amount))}"),
                        mouseX,
                        mouseY,
                    )
                } else {
                    context.setTooltipForNextFrame(font, stack, mouseX, mouseY)
                }
            }
        } else {
            val label = displayedIngredient.displayName ?: displayedIngredient.id.replace('_', ' ')
            LegacyTextRenderer.draw(
                context,
                "§6${ItemListFormatting.number(displayedIngredient.count)}",
                bounds.x + 2,
                bounds.y + 2,
            )
            if (bounds.contains(mouseX, mouseY)) context.setTooltipForNextFrame(font, Component.literal(label), mouseX, mouseY)
        }
        return key
    }

    fun navigateIngredient(mouseX: Int, mouseY: Int, canQuickCraft: Boolean = false): ViewerInputResult {
        val craftingHelperTarget = craftingHelperBounds.firstOrNull { it.first.contains(mouseX, mouseY) }?.second
        if (canQuickCraft && SkysoftConfigGui.config().inventory.craftingHelper.enabled && craftingHelperTarget != null) {
            addCraftingHelperTarget(craftingHelperTarget)
            return ViewerInputResult.HANDLED
        }
        val quickCraftCommand = quickCraftBounds.firstOrNull { it.first.contains(mouseX, mouseY) }?.second
        if (canQuickCraft && quickCraftCommand != null) {
            val connection = Minecraft.getInstance().connection
            if (connection == null) return ViewerInputResult.IGNORED
            connection.sendCommand(quickCraftCommand)
            return ViewerInputResult.HANDLED
        }
        val key = ingredientBounds.firstOrNull { it.first.contains(mouseX, mouseY) }?.second
            ?: return ViewerInputResult.IGNORED
        return selection.navigateTo(key)
    }

    private companion object {
        const val MAX_CATEGORY_BUTTONS = 6
        private const val MAX_PROCESS_INGREDIENTS = 7
    }
}

private fun renderProgressionRequirement(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    requirement: SkyBlockProgressionRequirement,
    mouseX: Int,
    mouseY: Int,
): Pair<Rect, SkyBlockProgressionRequirement> {
    val text = requirement.displayText
    val icon = when (requirement.iconKind) {
        SkyBlockProgressionIconKind.ITEM ->
            SkyBlockDataRepository.displayStack(SkyBlockDataRepository.itemKey(requirement.iconId))
        SkyBlockProgressionIconKind.ENTITY -> SkyBlockEntityStacks.stack(requirement.iconId)
    }
    val iconWidth = if (icon == null) 0 else PROGRESSION_ICON_SIZE + PROGRESSION_ICON_GAP
    val startX = bounds.x + (bounds.width - iconWidth - font.width(text)) / 2
    if (icon != null) context.item(icon, startX, bounds.y + 1)
    LegacyTextRenderer.draw(
        context,
        if (bounds.contains(mouseX, mouseY)) "§e$text" else "§f$text",
        startX + iconWidth,
        bounds.y + PROGRESSION_TEXT_Y,
    )
    if (bounds.contains(mouseX, mouseY)) {
        SkysoftNativeTooltip.setForNextFrame(context, listOf("§e${requirement.actionTooltip}"), mouseX, mouseY)
    }
    return bounds to requirement
}

private fun sendProgressionCommandAt(
    progressionBounds: List<Pair<Rect, SkyBlockProgressionRequirement>>,
    mouseX: Int,
    mouseY: Int,
): ViewerInputResult {
    val requirement = progressionBounds.firstOrNull { (bounds, _) -> bounds.contains(mouseX, mouseY) }?.second
        ?: return ViewerInputResult.IGNORED
    val connection = Minecraft.getInstance().connection ?: return ViewerInputResult.IGNORED
    connection.sendCommand(requirement.command)
    return ViewerInputResult.HANDLED
}

private fun renderProcessDetails(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    recipe: SkyBlockRecipe.Process,
    petLevels: Map<RecipePetLevelKey, Int>,
) {
    val lines = buildList {
        val coins = displayedProcessCoins(recipe, petLevels)
        if (coins > 0) add("§6${ItemListFormatting.number(coins)} Coins")
        if (recipe.durationSeconds > 0) add("§e${ItemListFormatting.duration(recipe.durationSeconds)}")
    }
    lines.take(MAX_PROCESS_DETAIL_LINES).forEachIndexed { index, line ->
        LegacyTextRenderer.draw(
            context,
            line,
            bounds.x + (bounds.width - font.width(line)) / 2,
            bounds.y + index * PROCESS_DETAIL_LINE_HEIGHT,
        )
    }
}

private fun drawCurrencyAmount(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    amount: Long,
    color: String,
) {
    val text = ItemListFormatting.compactNumber(amount)
    LegacyTextRenderer.draw(
        context,
        "$color$text",
        bounds.x + bounds.width - font.width(text) - CURRENCY_COUNT_RIGHT_INSET,
        bounds.y + bounds.height - CURRENCY_COUNT_BOTTOM_INSET,
    )
}

internal data class ItemListRecipePagination(
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val label: String,
)

private data class PetIngredientBounds(
    val bounds: Rect,
    val ingredientId: String,
    val levelKey: RecipePetLevelKey,
)

private const val MAX_PROCESS_DETAIL_LINES = 3
private const val PROCESS_DETAIL_LINE_HEIGHT = 12
private const val PROGRESSION_ICON_SIZE = 16
private const val PROGRESSION_ICON_GAP = 3
private const val PROGRESSION_TEXT_Y = 5
private const val CURRENCY_COUNT_RIGHT_INSET = 1
private const val CURRENCY_COUNT_BOTTOM_INSET = 8
