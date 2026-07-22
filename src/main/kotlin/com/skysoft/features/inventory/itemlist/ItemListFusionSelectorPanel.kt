package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.RecipeIngredient
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.expandedOptions
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class ItemListFusionSelectorPanel {
    private val selectedIds = mutableMapOf<FusionIngredientTarget, String>()
    private val sortedOptions = mutableMapOf<FusionIngredientTarget, List<RecipeIngredient>>()
    private var openTarget: FusionIngredientTarget? = null
    private var anchor: Rect? = null
    private var page = 0
    private var renderedDropdown: ItemListTierDropdown? = null
    private var renderedOptions: List<Pair<Rect, RecipeIngredient>> = emptyList()
    private var renderedPage: FusionDropdownPage? = null

    val isOpen: Boolean get() = openTarget != null

    fun selectedIngredient(target: FusionIngredientTarget): RecipeIngredient? {
        val selectedId = selectedIds[target] ?: return null
        return target.ingredient.expandedOptions().firstOrNull { it.id == selectedId }
    }

    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        panel: Rect,
        recipes: List<SkyBlockRecipe>,
        mouseX: Int,
        mouseY: Int,
    ) {
        val target = openTarget ?: return
        val currentAnchor = anchor ?: return resetSelector()
        if (target.recipe !in recipes) return resetSelector()
        val options = options(target)
        val currentPage = FusionDropdownPage.create(
            options.size,
            page,
            FusionDropdownPage.optionsPerPage(panel.height),
        )
        page = currentPage.page
        renderedPage = currentPage
        val dropdown = ItemListTierDropdown.create(panel, currentAnchor, currentPage.cellCount, DROPDOWN_SLOT_SIZE)
        renderedDropdown = dropdown
        dropdown.renderBackground(context)
        val previous = dropdown.tierBounds[PREVIOUS_CELL]
        val next = dropdown.tierBounds[NEXT_CELL]
        PixelButtonRenderer.draw(
            context,
            font,
            previous,
            "<",
            false,
            previous.contains(mouseX, mouseY),
            currentPage.page > 0,
        )
        PixelButtonRenderer.draw(
            context,
            font,
            next,
            ">",
            false,
            next.contains(mouseX, mouseY),
            currentPage.page + 1 < currentPage.pageCount,
        )
        val labelBounds = Rect(
            dropdown.tierBounds[PAGE_LABEL_START_CELL].x,
            dropdown.tierBounds[PAGE_LABEL_START_CELL].y,
            next.x - dropdown.tierBounds[PAGE_LABEL_START_CELL].x,
            dropdown.tierBounds[PAGE_LABEL_START_CELL].height,
        )
        val label = "Input ${target.ingredientIndex + 1}  ${currentPage.page + 1}/${currentPage.pageCount}"
        LegacyTextRenderer.draw(
            context,
            "§7$label",
            labelBounds.x + (labelBounds.width - font.width(label)) / 2,
            labelBounds.y + PAGE_LABEL_Y,
        )
        val visibleOptions = options.slice(currentPage.optionIndices)
        renderedOptions = visibleOptions.mapIndexed { index, option ->
            val bounds = dropdown.tierBounds[HEADER_CELL_COUNT + index]
            renderOption(context, font, bounds, option, selectedIds[target] == option.id, mouseX, mouseY)
            bounds to option
        }
    }

    fun click(triggers: List<FusionIngredientTrigger>, mouseX: Int, mouseY: Int): ViewerInputResult {
        val clickedTrigger = triggers.firstOrNull { it.bounds.contains(mouseX, mouseY) }
        if (openTarget == null) {
            clickedTrigger ?: return ViewerInputResult.IGNORED
            open(clickedTrigger)
            return ViewerInputResult.HANDLED
        }
        renderedOptions.firstOrNull { it.first.contains(mouseX, mouseY) }?.second?.let { option ->
            selectedIds[requireNotNull(openTarget)] = option.id
            resetSelector()
            return ViewerInputResult.HANDLED
        }
        val dropdown = renderedDropdown
        val currentPage = renderedPage
        if (dropdown != null && currentPage != null) {
            return when {
                dropdown.tierBounds[PREVIOUS_CELL].contains(mouseX, mouseY) && page > 0 -> {
                    page--
                    ViewerInputResult.PREVIOUS_PAGE
                }
                dropdown.tierBounds[NEXT_CELL].contains(mouseX, mouseY) && page + 1 < currentPage.pageCount -> {
                    page++
                    ViewerInputResult.NEXT_PAGE
                }
                clickedTrigger != null -> {
                    open(clickedTrigger)
                    ViewerInputResult.HANDLED
                }
                dropdown.bounds.contains(mouseX, mouseY) -> ViewerInputResult.HANDLED
                else -> {
                    resetSelector()
                    ViewerInputResult.IGNORED
                }
            }
        }
        return ViewerInputResult.IGNORED
    }

    fun closeSelector(): ViewerInputResult {
        if (!isOpen) return ViewerInputResult.IGNORED
        resetSelector()
        return ViewerInputResult.HANDLED
    }

    private fun open(trigger: FusionIngredientTrigger) {
        openTarget = trigger.target
        anchor = trigger.bounds
        page = 0
        renderedDropdown = null
        renderedOptions = emptyList()
        renderedPage = null
    }

    private fun renderOption(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        ingredient: RecipeIngredient,
        isSelected: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        context.fill(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            if (isSelected) SELECTED_BORDER else ItemListSlotStyle.BORDER,
        )
        context.fill(
            bounds.x + 1,
            bounds.y + 1,
            bounds.x + bounds.width - 1,
            bounds.y + bounds.height - 1,
            ItemListSlotStyle.FILL,
        )
        val key = SkyBlockDataRepository.itemKey(ingredient.id)
        val stack = SkyBlockDataRepository.displayStack(key)?.withActionHint("SELECT") ?: return
        context.item(stack, bounds.x + 1, bounds.y + 1)
        if (ingredient.count > 1) {
            context.itemDecorations(font, stack, bounds.x + 1, bounds.y + 1, ItemListFormatting.number(ingredient.count))
        }
        if (bounds.contains(mouseX, mouseY)) context.setTooltipForNextFrame(font, stack, mouseX, mouseY)
    }

    private fun options(target: FusionIngredientTarget): List<RecipeIngredient> =
        sortedOptions.getOrPut(target) {
            target.ingredient.expandedOptions().sortedBy { ingredient ->
                SkyBlockDataRepository.entry(SkyBlockDataRepository.itemKey(ingredient.id))
                    ?.displayName?.lowercase(Locale.ROOT) ?: ingredient.id.lowercase(Locale.ROOT)
            }
        }

    private fun resetSelector() {
        openTarget = null
        anchor = null
        renderedDropdown = null
        renderedOptions = emptyList()
        renderedPage = null
    }

    private companion object {
        const val DROPDOWN_SLOT_SIZE = 18
        const val HEADER_CELL_COUNT = 9
        const val PREVIOUS_CELL = 0
        const val PAGE_LABEL_START_CELL = 1
        const val NEXT_CELL = 8
        const val PAGE_LABEL_Y = 5
        val SELECTED_BORDER = 0xFF55FFFF.toInt()
    }
}

internal data class FusionIngredientTarget(
    val recipe: SkyBlockRecipe.Process,
    val ingredientIndex: Int,
) {
    val ingredient: RecipeIngredient get() = recipe.ingredients[ingredientIndex]
}

internal data class FusionIngredientTrigger(
    val bounds: Rect,
    val target: FusionIngredientTarget,
)

internal data class FusionDropdownPage(
    val page: Int,
    val pageCount: Int,
    val cellCount: Int,
    val optionIndices: IntRange,
) {
    companion object {
        fun create(
            optionCount: Int,
            requestedPage: Int,
            optionsPerPage: Int = MAX_OPTIONS_PER_PAGE,
        ): FusionDropdownPage {
            require(optionCount > 0)
            require(optionsPerPage > 0)
            val pageCount = Math.ceilDiv(optionCount, optionsPerPage)
            val page = requestedPage.coerceIn(0, pageCount - 1)
            val firstOption = page * optionsPerPage
            val lastOption = minOf(optionCount, firstOption + optionsPerPage) - 1
            val cellCount = HEADER_CELL_COUNT + if (pageCount > 1) optionsPerPage else optionCount
            return FusionDropdownPage(page, pageCount, cellCount, firstOption..lastOption)
        }

        fun optionsPerPage(panelHeight: Int): Int {
            val rows = ((panelHeight - DROPDOWN_PADDING) / DROPDOWN_SLOT_SIZE).coerceAtLeast(2)
            return (rows - 1).coerceAtMost(MAX_OPTION_ROWS) * DROPDOWN_COLUMNS
        }

        private const val MAX_OPTIONS_PER_PAGE = 36
        private const val HEADER_CELL_COUNT = 9
        private const val DROPDOWN_COLUMNS = 9
        private const val MAX_OPTION_ROWS = 4
        private const val DROPDOWN_PADDING = 4
        private const val DROPDOWN_SLOT_SIZE = 18
    }
}
