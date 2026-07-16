package com.skysoft.features.inventory.itemlist

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListTierFamily
import com.skysoft.data.skyblock.ItemListTierFamilyKind
import com.skysoft.data.skyblock.RecipeIngredient
import com.skysoft.data.skyblock.RecipeIngredientKind
import com.skysoft.data.skyblock.SkyBlockCurrencyStacks
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockEntityStacks
import com.skysoft.data.skyblock.SkyBlockProgressionIconKind
import com.skysoft.data.skyblock.SkyBlockProgressionRequirement
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType
import com.skysoft.data.skyblock.recipeIngredientStack
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.BrowserUtilities
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderAt
import kotlin.math.min
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

internal class ItemListViewerScreen(
    private val parent: Screen?,
    initialKey: ItemListEntryKey,
    initialMode: ItemListViewMode = ItemListViewMode.INFO,
) : Screen(Component.literal("Skysoft Item List")) {
    private val selection = ViewerSelection(initialKey, initialMode)
    private var currentKey by selection::currentKey
    private var mode by selection::mode
    private var selectedType by selection::selectedType
    private var selectedSupplemental by selection::selectedSupplemental
    private var recipePage by selection::recipePage
    private val backStack = mutableListOf<ViewerLocation>()
    private val forwardStack = mutableListOf<ViewerLocation>()
    private val infoPanel = ItemListInfoPanel()
    private val obtainSourcesPanel = ItemListObtainSourcesPanel()
    private val bazaarPanel = ItemListBazaarPanel()
    private val auctionHousePanel = ItemListAuctionHousePanel()
    private val fusionSelectorPanel = ItemListFusionSelectorPanel()
    private var layout: ViewerLayout? = null
    private var ingredientBounds: List<Pair<Rect, ItemListEntryKey>> = emptyList()
    private var progressionBounds: List<Pair<Rect, SkyBlockProgressionRequirement>> = emptyList()
    private var entityBounds: List<Pair<Rect, String>> = emptyList()
    private var petBounds: List<PetIngredientBounds> = emptyList()
    private var fusionIngredientTriggers: List<FusionIngredientTrigger> = emptyList()
    private val petLevels = mutableMapOf<RecipePetLevelKey, Int>()

    override fun init() {
        super.init()
        SkyBlockPriceData.setItemListMarketInterest(true)
        SkyBlockPriceData.refreshItemListMarketNow()
    }

    override fun removed() {
        SkyBlockPriceData.setItemListMarketInterest(false)
        super.removed()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        selection.ensureAvailableMode()
        if (mode != ItemListViewMode.RECIPES || selectedSupplemental != null) fusionSelectorPanel.closeSelector()
        context.fill(0, 0, width, height, SCREEN_OVERLAY)
        val currentLayout = ViewerLayout.create(width, height)
        layout = currentLayout
        OverlayPanelStyle.draw(
            context,
            currentLayout.panel.x,
            currentLayout.panel.y,
            currentLayout.panel.width,
            currentLayout.panel.height,
        )
        renderHeader(context, currentLayout, mouseX, mouseY)
        renderTabs(context, currentLayout, mouseX, mouseY)
        when (mode) {
            ItemListViewMode.INFO -> {
                ingredientBounds = emptyList()
                progressionBounds = emptyList()
                entityBounds = emptyList()
                petBounds = emptyList()
                fusionIngredientTriggers = emptyList()
                infoPanel.render(context, font, currentLayout.content, currentKey, mouseX, mouseY)
                renderInfoLinks(context, font, currentLayout, currentKey, mouseX, mouseY)
            }
            ItemListViewMode.RECIPES, ItemListViewMode.USAGES -> renderRecipes(context, currentLayout, mouseX, mouseY)
        }
        renderFooter(context, currentLayout, mouseX, mouseY)
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val currentLayout = layout ?: return super.mouseClicked(click, doubled)
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()
        val result = when (click.button()) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> applyLeftClick(currentLayout, mouseX, mouseY)
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> navigateIngredient(mouseX, mouseY)
            else -> ViewerInputResult.IGNORED
        }
        if (!result.isHandled) return super.mouseClicked(click, doubled)
        SoundUtilities.playClickSound()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val currentLayout = layout ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
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
        if (mode != ItemListViewMode.INFO) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val result = infoPanel.applyScroll(currentLayout.content, mouseX.toInt(), mouseY.toInt(), scrollY)
        return if (result.isHandled) true else super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() in listOf(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE) &&
            fusionSelectorPanel.closeSelector().isHandled
        ) {
            return true
        }
        itemListShortcutMode(event.key(), SkysoftConfigGui.config().inventory.itemList.settings)?.let {
            selection.changeMode(it)
            lastItemListShortcutOutcome = "$it selected:viewer"
            return true
        }
        return when (event.key()) {
            GLFW.GLFW_KEY_BACKSPACE -> navigateBack().isHandled
            GLFW.GLFW_KEY_LEFT -> selection.changePage(-1, layout?.recipeGrid?.pageSize ?: 1, auctionHousePanel).isHandled
            GLFW.GLFW_KEY_RIGHT -> selection.changePage(1, layout?.recipeGrid?.pageSize ?: 1, auctionHousePanel).isHandled
            GLFW.GLFW_KEY_A -> {
                ItemListState.toggleFavorite(currentKey)
                true
            }
            else -> super.keyPressed(event)
        }
    }

    override fun onClose() {
        MinecraftClient.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private fun applyLeftClick(layout: ViewerLayout, mouseX: Int, mouseY: Int): ViewerInputResult = when {
        layout.close.contains(mouseX, mouseY) -> {
            onClose()
            ViewerInputResult.HANDLED
        }
        layout.back.contains(mouseX, mouseY) -> navigateBack()
        layout.forward.contains(mouseX, mouseY) -> {
            val location = forwardStack.removeLastOrNull() ?: return ViewerInputResult.IGNORED
            backStack += selection.location()
            selection.restore(location)
            ViewerInputResult.HANDLED
        }
        layout.favorite.contains(mouseX, mouseY) -> {
            ItemListState.toggleFavorite(currentKey)
            ViewerInputResult.HANDLED
        }
        layout.tierPrevious.contains(mouseX, mouseY) ->
            navigateMinionTier(selection, -1, backStack, forwardStack)
        layout.tierNext.contains(mouseX, mouseY) ->
            navigateMinionTier(selection, 1, backStack, forwardStack)
        layout.infoTab.contains(mouseX, mouseY) -> selection.changeMode(ItemListViewMode.INFO)
        layout.recipeTab.contains(mouseX, mouseY) -> selection.changeMode(ItemListViewMode.RECIPES)
        layout.usageTab.contains(mouseX, mouseY) -> selection.changeMode(ItemListViewMode.USAGES)
        layout.previous.contains(mouseX, mouseY) -> selection.changePage(-1, layout.recipeGrid.pageSize, auctionHousePanel)
        layout.next.contains(mouseX, mouseY) -> selection.changePage(1, layout.recipeGrid.pageSize, auctionHousePanel)
        layout.wikiLinks(minionFamily(currentKey) != null).first.contains(mouseX, mouseY) ->
            openWiki(currentKey, official = true)
        layout.wikiLinks(minionFamily(currentKey) != null).second.contains(mouseX, mouseY) ->
            openWiki(currentKey, official = false)
        else -> run {
            val categories = selection.currentCategories().take(MAX_CATEGORY_BUTTONS)
            categories.withIndex().firstOrNull { (index, _) -> layout.category(index).contains(mouseX, mouseY) }
                ?.value?.let(selection::selectCategory) ?: ViewerInputResult.IGNORED
        }.orElse {
            auctionHousePanel.click(
                mode == ItemListViewMode.RECIPES && selectedSupplemental == ViewerSupplementalCategory.AUCTION_HOUSE,
                layout.recipeGrid.bounds,
                mouseX,
                mouseY,
            )
        }.orElse {
            if (mode == ItemListViewMode.RECIPES && selectedSupplemental == ViewerSupplementalCategory.BAZAAR) {
                val click = bazaarPanel.click(layout.recipeGrid.bounds, currentKey, mouseX, mouseY)
                if (click.isHandled) ViewerInputResult.HANDLED else ViewerInputResult.IGNORED
            } else {
                ViewerInputResult.IGNORED
            }
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
            val npcAction = requestNpcWarpAt(mode, infoPanel, entityBounds, mouseX, mouseY)
            if (npcAction.shouldCloseScreen) MinecraftClient.setScreen(null)
            npcAction.inputResult
        }.orElse {
            navigateIngredient(mouseX, mouseY)
        }
    }

    private fun renderHeader(context: GuiGraphicsExtractor, layout: ViewerLayout, mouseX: Int, mouseY: Int) {
        val entry = SkyBlockDataRepository.entry(currentKey)
        val stack = SkyBlockDataRepository.displayStack(currentKey)
        if (stack != null) {
            context.item(stack, layout.item.x + 1, layout.item.y + 1)
            if (layout.item.contains(mouseX, mouseY)) context.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
        LegacyTextRenderer.draw(
            context,
            stack?.hoverName?.string ?: entry?.displayName ?: currentKey.id,
            layout.title.x,
            layout.title.y,
            shadow = false,
        )
        LegacyTextRenderer.draw(
            context,
            "§8${entry?.source ?: currentKey.kind.name.lowercase()}",
            layout.title.x,
            layout.title.y + LINE_HEIGHT,
            shadow = false,
        )
        PixelButtonRenderer.draw(
            context,
            font,
            layout.back,
            "<",
            false,
            layout.back.contains(mouseX, mouseY),
            backStack.isNotEmpty(),
        )
        PixelButtonRenderer.draw(
            context,
            font,
            layout.forward,
            ">",
            false,
            layout.forward.contains(mouseX, mouseY),
            forwardStack.isNotEmpty(),
        )
        renderFavoriteButton(context, font, layout.favorite, currentKey, mouseX, mouseY)
        PixelButtonRenderer.draw(context, font, layout.close, "X", false, layout.close.contains(mouseX, mouseY), true)
    }

    private fun renderTabs(context: GuiGraphicsExtractor, layout: ViewerLayout, mouseX: Int, mouseY: Int) {
        drawViewerTab(context, font, layout.infoTab, "Info", mode == ItemListViewMode.INFO, mouseX, mouseY, true, null)
        drawViewerTab(
            context,
            font,
            layout.recipeTab,
            "Obtain",
            mode == ItemListViewMode.RECIPES,
            mouseX,
            mouseY,
            selection.hasObtainMethods(),
            "No obtain methods found",
        )
        drawViewerTab(
            context,
            font,
            layout.usageTab,
            "Uses",
            mode == ItemListViewMode.USAGES,
            mouseX,
            mouseY,
            selection.hasUsages(),
            "No uses found",
        )
    }

    private fun renderRecipes(context: GuiGraphicsExtractor, layout: ViewerLayout, mouseX: Int, mouseY: Int) {
        progressionBounds = emptyList()
        entityBounds = emptyList()
        petBounds = emptyList()
        fusionIngredientTriggers = emptyList()
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
        recipePage = recipePage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
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

    private fun renderFooter(context: GuiGraphicsExtractor, layout: ViewerLayout, mouseX: Int, mouseY: Int) {
        val minionFamily = minionFamily(currentKey)
        renderTierFooter(context, font, layout, currentKey, minionFamily, mouseX, mouseY)
        if (mode == ItemListViewMode.INFO) return
        if (fusionSelectorPanel.isOpen) return
        if (selectedSupplemental == ViewerSupplementalCategory.AUCTION_HOUSE) {
            layout.renderPageFooter(
                context,
                font,
                mouseX,
                mouseY,
                auctionHousePanel.canGoPrevious,
                auctionHousePanel.canGoNext,
                auctionHousePanel.pageLabel,
                minionFamily != null,
            )
            return
        }
        if (selectedSupplemental != null) return
        val recipes = selection.selectedRecipes(selection.currentRecipes())
        val pageCount = recipePageCount(recipes.size, layout.recipeGrid.pageSize)
        layout.renderPageFooter(
            context,
            font,
            mouseX,
            mouseY,
            recipePage > 0,
            recipePage + 1 < pageCount,
            if (pageCount == 0) "0 / 0" else "${recipePage + 1} / $pageCount",
            minionFamily != null,
        )
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
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, SLOT_BORDER)
        context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, SLOT_FILL)
        if (ingredient == null) return null
        val displayedIngredient = displayedOverride ?: displayedRecipeIngredient(ingredient)
        val key = displayedIngredient.itemKey()
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
                currencyStack != null -> drawCurrencyAmount(context, font, bounds, requireNotNull(currencyAmount))
            }
            if (bounds.contains(mouseX, mouseY)) {
                if (currencyStack != null) {
                    val amount = requireNotNull(currencyAmount)
                    SkysoftNativeTooltip.setForNextFrame(
                        context,
                        listOf("§6${requireNotNull(SkyBlockCurrencyStacks.supportedName(ingredient.id, amount))}"),
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

    private fun navigateTo(key: ItemListEntryKey): ViewerInputResult {
        if (key == currentKey && mode == ItemListViewMode.INFO) return ViewerInputResult.IGNORED
        backStack += selection.location()
        forwardStack.clear()
        selection.open(key)
        ItemListState.recordRecent(key)
        return ViewerInputResult.HANDLED
    }

    private fun navigateIngredient(mouseX: Int, mouseY: Int): ViewerInputResult {
        val key = ingredientBounds.firstOrNull { it.first.contains(mouseX, mouseY) }?.second
            ?: return ViewerInputResult.IGNORED
        return navigateTo(key)
    }

    private fun navigateBack(): ViewerInputResult {
        val location = backStack.removeLastOrNull() ?: return ViewerInputResult.IGNORED
        forwardStack += selection.location()
        selection.restore(location)
        return ViewerInputResult.HANDLED
    }

    private companion object {
        const val MAX_CATEGORY_BUTTONS = 6
        const val MAX_PROCESS_INGREDIENTS = 7
        const val LINE_HEIGHT = 12
        val SCREEN_OVERLAY = 0xB0000000.toInt()
        val SLOT_BORDER = 0xFF111315.toInt()
        val SLOT_FILL = 0xD0202428.toInt()
    }
}

private fun minionFamily(key: ItemListEntryKey): ItemListTierFamily? =
    SkyBlockDataRepository.ItemListData.tierFamily(key)?.takeIf { it.kind == ItemListTierFamilyKind.MINION }

private fun navigateMinionTier(
    selection: ViewerSelection,
    delta: Int,
    backStack: MutableList<ViewerLocation>,
    forwardStack: MutableList<ViewerLocation>,
): ViewerInputResult {
    val family = minionFamily(selection.currentKey) ?: return ViewerInputResult.IGNORED
    val currentIndex = family.tiers.indexOf(selection.currentKey)
    val nextKey = family.tiers.getOrNull(currentIndex + delta) ?: return ViewerInputResult.IGNORED
    backStack += selection.location()
    forwardStack.clear()
    selection.open(nextKey)
    ItemListState.recordRecent(nextKey)
    return ViewerInputResult.HANDLED
}

private fun renderTierFooter(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    layout: ViewerLayout,
    key: ItemListEntryKey,
    family: ItemListTierFamily?,
    mouseX: Int,
    mouseY: Int,
) {
    if (family == null) return
    val index = family.tiers.indexOf(key)
    PixelButtonRenderer.draw(
        context,
        font,
        layout.tierPrevious,
        "<",
        false,
        layout.tierPrevious.contains(mouseX, mouseY),
        index > 0,
    )
    PixelButtonRenderer.draw(
        context,
        font,
        layout.tierNext,
        ">",
        false,
        layout.tierNext.contains(mouseX, mouseY),
        index + 1 < family.tiers.size,
    )
    drawViewerCentered(context, font, layout.tierPage, "${index + 1} / ${family.tiers.size}")
}

private fun renderFavoriteButton(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    key: ItemListEntryKey,
    mouseX: Int,
    mouseY: Int,
) {
    val isFavorite = ItemListState.isFavorite(key)
    val isHovered = bounds.contains(mouseX, mouseY)
    PixelButtonRenderer.draw(context, font, bounds, "", isFavorite, isHovered, true)
    context.blitSprite(
        RenderPipelines.GUI_TEXTURED,
        if (isFavorite) HEART_FULL else HEART_CONTAINER,
        bounds.x + (bounds.width - HEART_SIZE) / 2,
        bounds.y + (bounds.height - HEART_SIZE) / 2,
        HEART_SIZE,
        HEART_SIZE,
    )
    if (isHovered) {
        SkysoftNativeTooltip.setForNextFrame(context, listOf(favoriteTooltip(isFavorite)), mouseX, mouseY)
    }
}

private fun drawViewerTab(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    label: String,
    isSelected: Boolean,
    mouseX: Int,
    mouseY: Int,
    isEnabled: Boolean,
    disabledTooltip: String?,
) {
    val isHovered = bounds.contains(mouseX, mouseY)
    PixelButtonRenderer.draw(context, font, bounds, label, isSelected, isHovered, isEnabled)
    if (isHovered && !isEnabled && disabledTooltip != null) {
        SkysoftNativeTooltip.setForNextFrame(context, listOf(disabledTooltip), mouseX, mouseY)
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

private fun requestNpcWarpAt(
    mode: ItemListViewMode,
    infoPanel: ItemListInfoPanel,
    entityBounds: List<Pair<Rect, String>>,
    mouseX: Int,
    mouseY: Int,
): NpcWarpClickResult {
    val entityId = (if (mode == ItemListViewMode.INFO) infoPanel.entityAt(mouseX, mouseY) else null)
        ?: entityBounds.firstOrNull { (bounds, _) -> bounds.contains(mouseX, mouseY) }?.second
        ?: return NpcWarpClickResult.IGNORED
    return when (ItemListNpcWaypoint.requestWarp(entityId)) {
        NpcWarpRequestResult.WARP_SENT -> NpcWarpClickResult.WARP_SENT
        NpcWarpRequestResult.WAYPOINT_ACTIVATED -> NpcWarpClickResult.WAYPOINT_ACTIVATED
        NpcWarpRequestResult.REJECTED -> NpcWarpClickResult.IGNORED
    }
}

private enum class NpcWarpClickResult(
    val inputResult: ViewerInputResult,
    val shouldCloseScreen: Boolean,
) {
    IGNORED(ViewerInputResult.IGNORED, false),
    WARP_SENT(ViewerInputResult.HANDLED, false),
    WAYPOINT_ACTIVATED(ViewerInputResult.HANDLED, true),
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
) {
    val text = ItemListFormatting.compactNumber(amount)
    LegacyTextRenderer.draw(
        context,
        "§6$text",
        bounds.x + bounds.width - font.width(text) - COIN_COUNT_RIGHT_INSET,
        bounds.y + bounds.height - COIN_COUNT_BOTTOM_INSET,
    )
}

private fun renderInfoLinks(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    layout: ViewerLayout,
    key: ItemListEntryKey,
    mouseX: Int,
    mouseY: Int,
) {
    val links = SkyBlockDataRepository.wikiLinks(key)
    val hasTierNavigation = SkyBlockDataRepository.ItemListData.tierFamily(key)?.kind == ItemListTierFamilyKind.MINION
    val (officialWiki, independentWiki) = layout.wikiLinks(hasTierNavigation)
    PixelButtonRenderer.draw(
        context,
        font,
        officialWiki,
        if (hasTierNavigation) "Official" else "Official Wiki",
        false,
        officialWiki.contains(mouseX, mouseY),
        links?.official != null,
    )
    PixelButtonRenderer.draw(
        context,
        font,
        independentWiki,
        if (hasTierNavigation) "Independent" else "Independent Wiki",
        false,
        independentWiki.contains(mouseX, mouseY),
        links?.independent != null,
    )
}

private fun RecipeIngredient.itemKey(): ItemListEntryKey? = when (kind) {
    RecipeIngredientKind.ITEM -> SkyBlockDataRepository.itemKey(id)
    RecipeIngredientKind.REGISTRY_ITEM -> ItemListEntryKey(com.skysoft.data.skyblock.ItemListEntryKind.REGISTRY, id)
    RecipeIngredientKind.POTION -> ItemListEntryKey(
        com.skysoft.data.skyblock.ItemListEntryKind.REGISTRY,
        id.substringBefore('|'),
    )
    RecipeIngredientKind.PET,
    RecipeIngredientKind.CURRENCY,
    RecipeIngredientKind.ESSENCE,
    RecipeIngredientKind.SPECIAL,
    -> null
}

private fun drawViewerCentered(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    text: String,
) {
    context.text(
        font,
        text,
        bounds.x + (bounds.width - font.width(text)) / 2,
        bounds.y + (bounds.height - font.lineHeight) / 2,
        VIEWER_TEXT_COLOR,
        false,
    )
}

private const val HEART_SIZE = 9
private const val MAX_PROCESS_DETAIL_LINES = 3
private const val PROCESS_DETAIL_LINE_HEIGHT = 12
private const val PROGRESSION_ICON_SIZE = 16
private const val PROGRESSION_ICON_GAP = 3
private const val PROGRESSION_TEXT_Y = 5
private const val COIN_COUNT_RIGHT_INSET = 1
private const val COIN_COUNT_BOTTOM_INSET = 8
private val HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container")
private val HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full")
private val VIEWER_TEXT_COLOR = 0xFFE0E4E8.toInt()

private class ViewerSelection(
    var currentKey: ItemListEntryKey,
    var mode: ItemListViewMode,
) {
    var selectedType: SkyBlockRecipeType? = null
    var selectedSupplemental: ViewerSupplementalCategory? = null
    var recipePage = 0

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

    fun changePage(delta: Int, pageSize: Int, auctionHousePanel: ItemListAuctionHousePanel): ViewerInputResult {
        if (mode == ItemListViewMode.RECIPES && selectedSupplemental == ViewerSupplementalCategory.AUCTION_HOUSE) {
            return auctionHousePanel.changePage(delta)
        }
        if (mode == ItemListViewMode.INFO || selectedSupplemental != null) return ViewerInputResult.IGNORED
        val recipes = selectedRecipes(currentRecipes())
        val pageCount = recipePageCount(recipes.size, pageSize)
        val nextPage = (recipePage + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (nextPage == recipePage) return ViewerInputResult.IGNORED
        recipePage = nextPage
        return ViewerInputResult.HANDLED
    }

    fun open(key: ItemListEntryKey) {
        currentKey = key
        mode = ItemListViewMode.INFO
        selectedType = null
        selectedSupplemental = null
        recipePage = 0
    }

    fun location(): ViewerLocation = ViewerLocation(currentKey, mode, selectedType, selectedSupplemental, recipePage)

    fun restore(location: ViewerLocation) {
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

private data class ViewerCategory(
    val recipeType: SkyBlockRecipeType? = null,
    val supplemental: ViewerSupplementalCategory? = null,
) {
    val label: String get() = recipeType?.displayName ?: requireNotNull(supplemental).label
}

private enum class ViewerSupplementalCategory(val label: String) {
    AUCTION_HOUSE("Auction House"),
    SOURCES("Sources"),
    HUNTING("Hunting"),
    BAZAAR("Bazaar"),
}

private fun hasObtainMethods(key: ItemListEntryKey): Boolean =
    SkyBlockDataRepository.recipesFor(key).isNotEmpty() ||
        hasOtherObtainSources(key) ||
        hasObtainSourcesMatching(key, SPECIALIZED_DETAIL_MARKERS) ||
        hasAuctionHouseObtainSource(key) ||
        hasBazaarObtainSource(key)

private data class PetIngredientBounds(
    val bounds: Rect,
    val ingredientId: String,
    val levelKey: RecipePetLevelKey,
)

private fun openWiki(key: ItemListEntryKey, official: Boolean): ViewerInputResult {
    val links = SkyBlockDataRepository.wikiLinks(key) ?: return ViewerInputResult.IGNORED
    val url = if (official) links.official else links.independent
    url ?: return ViewerInputResult.IGNORED
    BrowserUtilities.open(url)
    return ViewerInputResult.HANDLED
}

private data class ViewerLayout(
    val panel: Rect,
    val item: Rect,
    val title: Rect,
    val back: Rect,
    val forward: Rect,
    val favorite: Rect,
    val close: Rect,
    val infoTab: Rect,
    val recipeTab: Rect,
    val usageTab: Rect,
    val content: Rect,
    val previous: Rect,
    val next: Rect,
    val recipePage: Rect,
    val officialWiki: Rect,
    val independentWiki: Rect,
    val officialWikiWithTiers: Rect,
    val independentWikiWithTiers: Rect,
    val tierPrevious: Rect,
    val tierNext: Rect,
    val tierPage: Rect,
    val recipePageWithTiers: Rect,
) {
    val recipeGrid = ViewerRecipeGrid.create(
        Rect(
            content.x,
            content.y + ViewerTabDimensions.CATEGORY_AREA_HEIGHT,
            content.width,
            content.height - ViewerTabDimensions.CATEGORY_AREA_HEIGHT,
        ),
    )

    fun category(index: Int): Rect {
        val width = (content.width - ViewerTabDimensions.CATEGORY_GAP * (ViewerTabDimensions.CATEGORY_COLUMNS - 1)) /
            ViewerTabDimensions.CATEGORY_COLUMNS
        return Rect(
            content.x + index % ViewerTabDimensions.CATEGORY_COLUMNS * (width + ViewerTabDimensions.CATEGORY_GAP),
            content.y + index / ViewerTabDimensions.CATEGORY_COLUMNS *
                (ViewerTabDimensions.CATEGORY_HEIGHT + ViewerTabDimensions.CATEGORY_GAP),
            width,
            ViewerTabDimensions.CATEGORY_HEIGHT,
        )
    }

    fun wikiLinks(hasTierNavigation: Boolean): Pair<Rect, Rect> = if (hasTierNavigation) {
        officialWikiWithTiers to independentWikiWithTiers
    } else {
        officialWiki to independentWiki
    }

    fun renderPageFooter(
        context: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        mouseX: Int,
        mouseY: Int,
        canGoPrevious: Boolean,
        canGoNext: Boolean,
        label: String,
        hasTierNavigation: Boolean,
    ) {
        PixelButtonRenderer.draw(
            context,
            font,
            previous,
            "<",
            false,
            previous.contains(mouseX, mouseY),
            canGoPrevious,
        )
        PixelButtonRenderer.draw(
            context,
            font,
            next,
            ">",
            false,
            next.contains(mouseX, mouseY),
            canGoNext,
        )
        drawViewerCentered(
            context,
            font,
            if (hasTierNavigation) recipePageWithTiers else recipePage,
            label,
        )
    }

    companion object {
        fun create(screenWidth: Int, screenHeight: Int): ViewerLayout {
            val panelWidth = min(ViewerPanelDimensions.MAX_WIDTH, screenWidth - ViewerPanelDimensions.SCREEN_INSET)
                .coerceAtLeast(ViewerPanelDimensions.MIN_WIDTH)
            val panelHeight = min(ViewerPanelDimensions.MAX_HEIGHT, screenHeight - ViewerPanelDimensions.SCREEN_INSET)
                .coerceAtLeast(ViewerPanelDimensions.MIN_HEIGHT)
            val panel = Rect((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight)
            val item = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                panel.y + ViewerPanelDimensions.PADDING,
                ViewerPanelDimensions.SLOT_SIZE,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val title = Rect(
                item.x + ViewerHeaderDimensions.TITLE_X_OFFSET,
                panel.y + ViewerHeaderDimensions.TITLE_Y_OFFSET,
                panel.width - ViewerHeaderDimensions.TITLE_RESERVED_WIDTH,
                ViewerHeaderDimensions.HEIGHT,
            )
            val close = Rect(
                panel.x + panel.width - ViewerHeaderDimensions.CLOSE_RIGHT,
                panel.y + ViewerHeaderDimensions.BUTTON_TOP,
                ViewerHeaderDimensions.BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val favorite = headerButtonBefore(close)
            val forward = headerButtonBefore(favorite)
            val back = headerButtonBefore(forward)
            val tabsY = panel.y + ViewerTabDimensions.Y_OFFSET
            val infoTab = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                tabsY,
                ViewerTabDimensions.WIDTH,
                ViewerTabDimensions.HEIGHT,
            )
            val recipeTab = tabAfter(infoTab)
            val usageTab = tabAfter(recipeTab)
            val footerY = panel.y + panel.height - ViewerFooterDimensions.BOTTOM
            val content = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                tabsY + ViewerTabDimensions.CONTENT_TOP_GAP,
                panel.width - ViewerPanelDimensions.SCREEN_INSET,
                footerY - tabsY - ViewerTabDimensions.CONTENT_FOOTER_GAP,
            )
            val footer = ViewerFooterLayout.create(panel, footerY)
            return ViewerLayout(
                panel,
                item,
                title,
                back,
                forward,
                favorite,
                close,
                infoTab,
                recipeTab,
                usageTab,
                content,
                footer.previous,
                footer.next,
                footer.recipePage,
                footer.officialWiki,
                footer.independentWiki,
                footer.officialWikiWithTiers,
                footer.independentWikiWithTiers,
                footer.tierPrevious,
                footer.tierNext,
                footer.tierPage,
                footer.recipePageWithTiers,
            )
        }

        private fun headerButtonBefore(button: Rect): Rect = Rect(
            button.x - ViewerHeaderDimensions.BUTTON_STEP,
            button.y,
            ViewerHeaderDimensions.BUTTON_WIDTH,
            ViewerPanelDimensions.SLOT_SIZE,
        )

        private fun tabAfter(tab: Rect): Rect = Rect(
            tab.x + ViewerTabDimensions.WIDTH + ViewerTabDimensions.GAP,
            tab.y,
            ViewerTabDimensions.WIDTH,
            ViewerTabDimensions.HEIGHT,
        )
    }
}

private data class ViewerFooterLayout(
    val previous: Rect,
    val next: Rect,
    val recipePage: Rect,
    val officialWiki: Rect,
    val independentWiki: Rect,
    val officialWikiWithTiers: Rect,
    val independentWikiWithTiers: Rect,
    val tierPrevious: Rect,
    val tierNext: Rect,
    val tierPage: Rect,
    val recipePageWithTiers: Rect,
) {
    companion object {
        fun create(panel: Rect, footerY: Int): ViewerFooterLayout {
            val previous = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                footerY,
                ViewerFooterDimensions.PAGE_BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val next = Rect(
                panel.x + panel.width - ViewerFooterDimensions.NEXT_RIGHT,
                footerY,
                ViewerFooterDimensions.PAGE_BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val tierPage = Rect(
                panel.x + (panel.width - ViewerFooterDimensions.TIER_PAGE_WIDTH) / 2,
                footerY,
                ViewerFooterDimensions.TIER_PAGE_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val tierPrevious = Rect(
                tierPage.x - ViewerFooterDimensions.TIER_BUTTON_WIDTH - ViewerFooterDimensions.TIER_GAP,
                footerY,
                ViewerFooterDimensions.TIER_BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val officialWiki = Rect(
                panel.x + ViewerFooterDimensions.WIKI_X_OFFSET,
                footerY,
                ViewerFooterDimensions.WIKI_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            return ViewerFooterLayout(
                previous = previous,
                next = next,
                recipePage = Rect(
                    previous.x + ViewerFooterDimensions.PAGE_LABEL_X_OFFSET,
                    footerY,
                    panel.width - ViewerFooterDimensions.PAGE_LABEL_RESERVED_WIDTH,
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
                officialWiki = officialWiki,
                independentWiki = Rect(
                    officialWiki.x + ViewerFooterDimensions.WIKI_WIDTH + ViewerFooterDimensions.WIKI_GAP,
                    footerY,
                    ViewerFooterDimensions.WIKI_WIDTH + ViewerFooterDimensions.INDEPENDENT_WIKI_EXTRA_WIDTH,
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
                officialWikiWithTiers = Rect(
                    panel.x + ViewerPanelDimensions.PADDING,
                    footerY,
                    ViewerFooterDimensions.TIER_WIKI_WIDTH,
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
                independentWikiWithTiers = Rect(
                    panel.x + panel.width - ViewerPanelDimensions.PADDING - ViewerFooterDimensions.TIER_WIKI_WIDTH,
                    footerY,
                    ViewerFooterDimensions.TIER_WIKI_WIDTH,
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
                tierPrevious = tierPrevious,
                tierNext = Rect(
                    tierPage.x + tierPage.width + ViewerFooterDimensions.TIER_GAP,
                    footerY,
                    ViewerFooterDimensions.TIER_BUTTON_WIDTH,
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
                tierPage = tierPage,
                recipePageWithTiers = Rect(
                    previous.x + previous.width + ViewerFooterDimensions.TIER_GAP,
                    footerY,
                    (tierPrevious.x - previous.x - previous.width - ViewerFooterDimensions.TIER_GAP * 2)
                        .coerceAtLeast(1),
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
            )
        }
    }
}

private object ViewerPanelDimensions {
    const val MAX_WIDTH = 430
    const val MIN_WIDTH = 300
    const val MAX_HEIGHT = 350
    const val MIN_HEIGHT = 220
    const val SCREEN_INSET = 20
    const val PADDING = 10
    const val SLOT_SIZE = 18
}

private object ViewerHeaderDimensions {
    const val TITLE_X_OFFSET = 24
    const val TITLE_Y_OFFSET = 9
    const val TITLE_RESERVED_WIDTH = 170
    const val HEIGHT = 30
    const val CLOSE_RIGHT = 30
    const val BUTTON_TOP = 8
    const val BUTTON_WIDTH = 22
    const val BUTTON_STEP = 26
}

private object ViewerTabDimensions {
    const val Y_OFFSET = 42
    const val WIDTH = 72
    const val HEIGHT = 19
    const val GAP = 4
    const val CONTENT_TOP_GAP = 24
    const val CONTENT_FOOTER_GAP = 28
    const val CATEGORY_GAP = 3
    const val CATEGORY_COLUMNS = 3
    const val CATEGORY_HEIGHT = 18
    const val CATEGORY_AREA_HEIGHT = CATEGORY_HEIGHT * 2 + CATEGORY_GAP + 4
}

private object ViewerFooterDimensions {
    const val BOTTOM = 30
    const val PAGE_BUTTON_WIDTH = 28
    const val NEXT_RIGHT = 38
    const val PAGE_LABEL_X_OFFSET = 32
    const val PAGE_LABEL_RESERVED_WIDTH = 84
    const val WIKI_X_OFFSET = 18
    const val WIKI_WIDTH = 112
    const val WIKI_GAP = 6
    const val INDEPENDENT_WIKI_EXTRA_WIDTH = 12
    const val TIER_PAGE_WIDTH = 52
    const val TIER_BUTTON_WIDTH = 22
    const val TIER_GAP = 4
    const val TIER_WIKI_WIDTH = 82
}
