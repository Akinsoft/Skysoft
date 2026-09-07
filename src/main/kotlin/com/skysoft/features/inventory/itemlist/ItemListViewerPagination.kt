package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt

internal data class ViewerCardGrid(
    val bounds: Rect,
    val columns: Int,
    val rows: Int,
    private val fixedTileHeight: Int? = null,
) {
    val pageSize: Int = columns * rows

    fun tile(index: Int, visibleCount: Int = pageSize): Rect {
        require(visibleCount in 1..pageSize) { "Visible card count $visibleCount is outside page size $pageSize" }
        require(index in 0 until visibleCount) { "Card index $index is outside visible count $visibleCount" }
        val gapWidth = (columns - 1) * TILE_GAP
        val gapHeight = (rows - 1) * TILE_GAP
        val tileWidth = (bounds.width - gapWidth) / columns
        val tileHeight = fixedTileHeight ?: (bounds.height - gapHeight) / rows
        val usedRows = Math.ceilDiv(visibleCount, columns)
        val row = index / columns
        val itemsInRow = minOf(columns, visibleCount - row * columns)
        val rowWidth = itemsInRow * tileWidth + (itemsInRow - 1) * TILE_GAP
        val usedHeight = usedRows * tileHeight + (usedRows - 1) * TILE_GAP
        return Rect(
            bounds.x + (bounds.width - rowWidth) / 2 + index % columns * (tileWidth + TILE_GAP),
            bounds.y + (bounds.height - usedHeight + 1) / 2 + row * (tileHeight + TILE_GAP),
            tileWidth,
            tileHeight,
        )
    }

    companion object {
        fun create(bounds: Rect): ViewerCardGrid = ViewerCardGrid(
            bounds = bounds,
            columns = if (bounds.width >= TWO_COLUMN_MIN_WIDTH) 2 else 1,
            rows = if (bounds.height >= TWO_ROW_MIN_HEIGHT) 2 else 1,
        )

        fun compact(bounds: Rect): ViewerCardGrid = ViewerCardGrid(
            bounds = bounds,
            columns = when {
                bounds.width >= THREE_COMPACT_COLUMN_MIN_WIDTH -> 3
                bounds.width >= TWO_COLUMN_MIN_WIDTH -> 2
                else -> 1
            },
            rows = ((bounds.height + TILE_GAP) / (COMPACT_TILE_HEIGHT + TILE_GAP)).coerceAtLeast(1),
            fixedTileHeight = COMPACT_TILE_HEIGHT,
        )

        private const val TILE_GAP = 6
        private const val COMPACT_TILE_HEIGHT = 40
        private const val TWO_COLUMN_MIN_WIDTH = 250
        private const val THREE_COMPACT_COLUMN_MIN_WIDTH = 360
        private const val TWO_ROW_MIN_HEIGHT = 154
    }
}

internal data class ViewerCraftingLayout(
    val slots: List<Rect>,
    val arrow: Rect,
    val result: Rect,
    val progressionRequirement: Rect?,
    val scale: Float,
) {
    companion object {
        fun create(tile: Rect, hasProgressionRequirement: Boolean = false): ViewerCraftingLayout {
            val baseContentWidth = GRID_SIZE + ITEM_GAP + ARROW_WIDTH + ITEM_GAP + SLOT_SIZE
            val scale = viewerContentScale(tile, baseContentWidth, GRID_SIZE)
            val slotSize = scaled(SLOT_SIZE, scale)
            val gridSize = CRAFTING_GRID_WIDTH * slotSize
            val itemGap = scaled(ITEM_GAP, scale)
            val arrowWidth = scaled(ARROW_WIDTH, scale)
            val arrowHeight = scaled(ARROW_HEIGHT, scale)
            val progressionWidth = minOf(tile.width, scaled(PROGRESSION_WIDTH, scale))
            val contentWidth = gridSize + itemGap + arrowWidth + itemGap + slotSize
            val gridX = tile.x + (tile.width - contentWidth) / 2
            val contentHeight = gridSize + if (hasProgressionRequirement) {
                PROGRESSION_GAP + PROGRESSION_HEIGHT
            } else {
                0
            }
            val gridY = tile.y + (tile.height - contentHeight) / 2
            val slots = List(CRAFTING_SLOT_COUNT) { index ->
                Rect(
                    gridX + index % CRAFTING_GRID_WIDTH * slotSize,
                    gridY + index / CRAFTING_GRID_WIDTH * slotSize,
                    slotSize,
                    slotSize,
                )
            }
            val arrow = Rect(
                gridX + gridSize + itemGap,
                gridY + gridSize / 2 - arrowHeight / 2,
                arrowWidth,
                arrowHeight,
            )
            val result = Rect(
                arrow.x + arrow.width + itemGap,
                gridY + gridSize / 2 - slotSize / 2,
                slotSize,
                slotSize,
            )
            val progressionRequirement = if (hasProgressionRequirement) {
                Rect(
                    gridX + gridSize / 2 - progressionWidth / 2,
                    gridY + gridSize + PROGRESSION_GAP,
                    progressionWidth,
                    PROGRESSION_HEIGHT,
                )
            } else {
                null
            }
            return ViewerCraftingLayout(slots, arrow, result, progressionRequirement, scale)
        }

        private const val CRAFTING_GRID_WIDTH = 3
        private const val CRAFTING_SLOT_COUNT = 9
        private const val SLOT_SIZE = 18
        private const val GRID_SIZE = CRAFTING_GRID_WIDTH * SLOT_SIZE
        private const val ITEM_GAP = 5
        private const val ARROW_WIDTH = 12
        private const val ARROW_HEIGHT = 9
        private const val PROGRESSION_GAP = 1
        private const val PROGRESSION_HEIGHT = 18
        private const val PROGRESSION_WIDTH = 128
    }
}

internal data class ViewerProcessLayout(
    val source: Rect?,
    val ingredients: List<Rect>,
    val arrow: Rect,
    val result: Rect,
    val details: Rect,
    val scale: Float,
) {
    companion object {
        fun create(tile: Rect, ingredientCount: Int, hasSource: Boolean = false): ViewerProcessLayout {
            val count = ingredientCount.coerceIn(0, MAX_INGREDIENTS)
            val ingredientColumns = count.coerceAtMost(MAX_INGREDIENT_COLUMNS).coerceAtLeast(1)
            val ingredientRows = Math.ceilDiv(count, MAX_INGREDIENT_COLUMNS)
            val baseIngredientWidth = ingredientColumns * SLOT_SIZE
            val baseSourceWidth = if (hasSource) SLOT_SIZE + SOURCE_GAP else 0
            val baseTotalWidth = baseSourceWidth + baseIngredientWidth + ITEM_GAP + ARROW_WIDTH + ITEM_GAP + SLOT_SIZE
            val baseTotalHeight = ITEM_TOP + ITEM_AREA_HEIGHT + DETAIL_GAP + DETAIL_HEIGHT
            val scale = viewerContentScale(tile, baseTotalWidth, baseTotalHeight)
            val slotSize = scaled(SLOT_SIZE, scale)
            val itemTop = scaled(ITEM_TOP, scale)
            val itemAreaHeight = scaled(ITEM_AREA_HEIGHT, scale)
            val itemGap = scaled(ITEM_GAP, scale)
            val sourceGap = scaled(SOURCE_GAP, scale)
            val arrowWidth = scaled(ARROW_WIDTH, scale)
            val arrowHeight = scaled(ARROW_HEIGHT, scale)
            val detailInset = scaled(DETAIL_INSET, scale)
            val detailGap = scaled(DETAIL_GAP, scale)
            val detailHeight = scaled(DETAIL_HEIGHT, scale)
            val itemAreaY = tile.y + itemTop
            val itemCenterY = itemAreaY + itemAreaHeight / 2
            val ingredientY = itemCenterY - ingredientRows * slotSize / 2
            val ingredientWidth = ingredientColumns * slotSize
            val sourceWidth = if (hasSource) slotSize + sourceGap else 0
            val totalWidth = sourceWidth + ingredientWidth + itemGap + arrowWidth + itemGap + slotSize
            val startX = tile.x + (tile.width - totalWidth) / 2
            val source = if (hasSource) {
                Rect(startX, itemCenterY - slotSize / 2, slotSize, slotSize)
            } else {
                null
            }
            val ingredientX = startX + sourceWidth
            val slots = List(count) { index ->
                Rect(
                    ingredientX + index % MAX_INGREDIENT_COLUMNS * slotSize,
                    ingredientY + index / MAX_INGREDIENT_COLUMNS * slotSize,
                    slotSize,
                    slotSize,
                )
            }
            val arrow = Rect(
                ingredientX + ingredientWidth + itemGap,
                itemCenterY - arrowHeight / 2,
                arrowWidth,
                arrowHeight,
            )
            val result = Rect(
                arrow.x + arrow.width + itemGap,
                itemCenterY - slotSize / 2,
                slotSize,
                slotSize,
            )
            return ViewerProcessLayout(
                source = source,
                ingredients = slots,
                arrow = arrow,
                result = result,
                details = Rect(
                    tile.x + detailInset,
                    itemAreaY + itemAreaHeight + detailGap,
                    tile.width - detailInset * 2,
                    detailHeight,
                ),
                scale = scale,
            )
        }

        private const val MAX_INGREDIENTS = 7
        private const val MAX_INGREDIENT_COLUMNS = 4
        private const val SLOT_SIZE = 18
        private const val ITEM_TOP = 4
        private const val ITEM_AREA_HEIGHT = 36
        private const val ITEM_GAP = 5
        private const val SOURCE_GAP = 4
        private const val ARROW_WIDTH = 12
        private const val ARROW_HEIGHT = 9
        private const val DETAIL_INSET = 4
        private const val DETAIL_GAP = 3
        private const val DETAIL_HEIGHT = 36
    }
}

private fun viewerContentScale(tile: Rect, baseWidth: Int, baseHeight: Int): Float = minOf(
    MAX_VIEWER_CONTENT_SCALE,
    tile.width.toFloat() / baseWidth,
    tile.height.toFloat() / baseHeight,
).coerceAtLeast(1f)

private fun scaled(value: Int, scale: Float): Int = (value * scale).roundToInt().coerceAtLeast(1)

internal fun recipePageCount(recipeCount: Int, pageSize: Int): Int = Math.ceilDiv(recipeCount, pageSize)
