package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerConfig
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.ProfitTrackerQuantityPosition
import com.skysoft.config.ProfitTrackerSummaryLine
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.pets.PetRepository
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.NumberUtilities.signedCoinFormat
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.withIsolatedPose
import kotlin.math.floor
import kotlin.math.roundToInt
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

private var hoveredControl: OverlayControlArea<ProfitTrackerControl>? = null
private var isTrackerHovered = false
private val itemPanel = ProfitTrackerItemPanel()
private val hudControls = ProfitTrackerHudControls(itemPanel)
private val itemScrollOffsets = mutableMapOf<ItemScrollKey, Int>()

internal fun registerProfitTrackerHud() {
    registerMouseCapture()
    ProfitTrackerPreset.entries.forEach(::registerProfitTrackerHudEditor)
    GuiOverlayRegistry.register(
        GuiOverlay(
            id = "profit_tracker",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = GuiOverlayContextType.entries.toSet(),
            render = { context, _ -> renderProfitTracker(context) },
        ),
    )
}

private fun registerProfitTrackerHudEditor(preset: ProfitTrackerPreset) {
    val config = presetConfig(preset)
    HudEditorRegistry.register(object : HudEditorElement {
        override val id: String = "profit_tracker_${preset.name.lowercase()}"
        override val label: String = "${preset.displayName} Profit Tracker"
        override val position get() = config.position
        override val hasEditorBackground: Boolean get() = !config.details.showBackground
        override fun width(): Int = buildProfitRenderable(preset, false).width
        override fun height(): Int = buildProfitRenderable(preset, false).height
        override fun isVisible(): Boolean = config.enabled
        override fun renderDummy(context: GuiGraphicsExtractor) = buildProfitRenderable(preset, false).render(context)
        override fun openConfig() = SkysoftConfigGui.open(preset.displayName)
    })
}

private fun renderProfitTracker(context: GuiGraphicsExtractor) {
    val minecraft = Minecraft.getInstance()
    if (!HypixelLocationState.inSkyBlock || MinecraftClient.isGuiHidden(minecraft)) {
        hoveredControl = null
        isTrackerHovered = false
        return
    }
    val preset = ProfitTracker.selectedPreset()?.takeIf(ProfitTracker::isInPresetArea) ?: run {
        hoveredControl = null
        isTrackerHovered = false
        return
    }
    val inventoryOpen = MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
    if (!inventoryOpen) itemPanel.clear()
    val renderable = buildProfitRenderable(preset, inventoryOpen)
    val window = minecraft.window
    val mouseX = minecraft.mouseHandler.getScaledXPos(window).toInt()
    val mouseY = minecraft.mouseHandler.getScaledYPos(window).toInt()
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    context.nextStratum()
    renderPositioned(context, renderable, preset, inventoryOpen, normalMouseX, normalMouseY)
    if (inventoryOpen) {
        context.nextStratum()
        hoveredControl?.let { area ->
            val (tooltipX, tooltipY) = OverlayControlMouse.deferredTooltipPoint(mouseX, mouseY)
            val managedItem = area.action as? ProfitTrackerControl.ManageItem
            if (managedItem != null) {
                SkysoftNativeTooltip.setItemActionForNextFrame(
                    context,
                    managedItem.stack,
                    "Manage",
                    managedItem.formattedName,
                    tooltipX,
                    tooltipY,
                )
            } else {
                SkysoftNativeTooltip.setForNextFrame(
                    context,
                    area.tooltipLines,
                    tooltipX,
                    tooltipY,
                    scrollable = false,
                )
            }
        }
    }
}

private fun renderPositioned(
    context: GuiGraphicsExtractor,
    renderable: ProfitTrackerRenderable,
    preset: ProfitTrackerPreset,
    interactive: Boolean,
    mouseX: Int,
    mouseY: Int,
) {
    val position = presetConfig(preset).position
    val scale = position.effectiveScale
    val scaledWidth = (renderable.width * scale).roundToInt()
    val scaledHeight = (renderable.height * scale).roundToInt()
    val x = position.getAbsX0AllowingOverflow(scaledWidth)
    val y = position.getAbsY0AllowingOverflow(scaledHeight)
    val localMouseX = floor((mouseX - x) / scale).toInt()
    val localMouseY = floor((mouseY - y) / scale).toInt()
    val placePanelRight = x + ((renderable.width + SIDE_PANEL_ESTIMATED_WIDTH) * scale).roundToInt() <=
        Minecraft.getInstance().window.guiScaledWidth
    val localControl = context.withIsolatedPose {
        pose().translate(x.toFloat(), y.toFloat())
        pose().scale(scale, scale)
        val trackerControl = renderable.renderInteractive(
            context,
            if (interactive) localMouseX else null,
            if (interactive) localMouseY else null,
        )
        val panelControl = itemPanel.render(
            context,
            preset,
            renderable.width,
            placePanelRight,
            if (interactive) localMouseX else Int.MIN_VALUE,
            if (interactive) localMouseY else Int.MIN_VALUE,
        )
        panelControl?.let { control ->
            LocalControlArea(
                control.action,
                control.x,
                control.y,
                control.width,
                control.height,
                control.tooltipLines,
            )
        } ?: trackerControl
    }
    isTrackerHovered = interactive && localMouseX in 0..renderable.width && localMouseY in 0..renderable.height
    hoveredControl = localControl?.let { area ->
        OverlayControlArea(
            action = area.action,
            x = x + (area.x * scale).roundToInt(),
            y = y + (area.y * scale).roundToInt(),
            width = (area.width * scale).roundToInt().coerceAtLeast(1),
            height = (area.height * scale).roundToInt().coerceAtLeast(1),
            tooltipLines = area.tooltipLines,
        )
    }
}

private fun buildProfitRenderable(preset: ProfitTrackerPreset, inventoryOpen: Boolean): ProfitTrackerRenderable {
    val config = presetConfig(preset)
    val stats = ProfitTracker.stats(preset)
    val items = profitDisplayItems(preset, stats)
    val maximumItems = config.settings.maximumItems.coerceIn(1, MAXIMUM_ITEMS)
    val scrollKey = ItemScrollKey(preset, ProfitTracker.displayPeriod(preset))
    val maximumOffset = (items.size - maximumItems).coerceAtLeast(0)
    val scrollOffset = itemScrollOffsets.getOrDefault(scrollKey, 0).coerceIn(0, maximumOffset)
    if (scrollOffset == 0) itemScrollOffsets.remove(scrollKey) else itemScrollOffsets[scrollKey] = scrollOffset
    return ProfitTrackerRenderable(
        preset = preset,
        stats = stats,
        items = items,
        maximumItems = maximumItems,
        scrollOffset = scrollOffset,
        inventoryOpen = inventoryOpen,
        config = config,
        background = config.details.showBackground,
    )
}

private fun registerMouseCapture() {
    ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
        if (ProfitTrackerPreset.entries.none { presetConfig(it).enabled } ||
            screen !is AbstractContainerScreen<*>
        ) return@register
        ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
            SkysoftErrorBoundary.value("Profit Tracker mouse click", true) {
                ProfitTracker.selectedPreset()?.let(::presetConfig)?.enabled != true ||
                    !hudControls.wasClickHandled(screen, hoveredControl?.action, click.button())
            }
        }
        ScreenMouseEvents.allowMouseScroll(screen).register { _, _, _, _, verticalAmount ->
            SkysoftErrorBoundary.value("Profit Tracker mouse scroll", true) {
                ProfitTracker.selectedPreset()?.let(::presetConfig)?.enabled != true ||
                    !isTrackerHovered || !wasItemScrollHandled(verticalAmount)
            }
        }
    }
}

private fun wasItemScrollHandled(verticalAmount: Double): Boolean {
    if (verticalAmount == 0.0) return false
    val preset = ProfitTracker.selectedPreset() ?: return false
    val period = ProfitTracker.displayPeriod(preset)
    val maximumItems = presetConfig(preset).settings.maximumItems.coerceIn(1, MAXIMUM_ITEMS)
    val maximumOffset = (profitDisplayItems(preset, ProfitTracker.stats(preset)).size - maximumItems).coerceAtLeast(0)
    if (maximumOffset == 0) return false
    val key = ItemScrollKey(preset, period)
    val current = itemScrollOffsets.getOrDefault(key, 0)
    itemScrollOffsets[key] = profitTrackerScrollOffset(current, verticalAmount, maximumOffset)
    return true
}

private fun profitDisplayItems(
    preset: ProfitTrackerPreset,
    stats: ProfileStorage.ProfitTrackerStats,
): List<ProfitDisplayItem> =
    stats.itemCounts.mapNotNull { (itemId, amount) ->
        if (itemId !in ProfitTracker.trackedItemIds(preset) || ProfitTrackerItemCustomizations.isExcluded(preset, itemId)) {
            return@mapNotNull null
        }
        val key = SkyBlockDataRepository.itemKey(itemId)
        val stack = SkyBlockDataRepository.displayStack(key) ?: PetRepository.itemStackOrNull(itemId) ?: return@mapNotNull null
        val name = (SkyBlockDataRepository.entry(key)?.formattedDisplayName ?: PetRepository.itemName(itemId) ?: itemId)
            .replace("Enchanted ", "Ench ")
        val unitValue = ProfitTracker.unitValue(preset, itemId)
        ProfitDisplayItem(itemId, name, stack, amount, unitValue?.times(amount))
    }.sortedWith(compareByDescending<ProfitDisplayItem> { it.value ?: Double.NEGATIVE_INFINITY }.thenBy { it.name })

private class ProfitTrackerRenderable(
    private val preset: ProfitTrackerPreset,
    private val stats: ProfileStorage.ProfitTrackerStats,
    items: List<ProfitDisplayItem>,
    maximumItems: Int,
    scrollOffset: Int,
    private val inventoryOpen: Boolean,
    private val config: ProfitTrackerConfig,
    private val background: Boolean,
) : GuiRenderable {
    private val displayedItems = items.drop(scrollOffset).take(maximumItems)
    private val remainingItems = (items.size - scrollOffset - displayedItems.size).coerceAtLeast(0)
    private val hiddenItemsAbove = scrollOffset
    private val revenue = items.sumOf { it.value ?: 0.0 } + stats.coins
    private val hasUnknownPrices = items.any { it.value == null }
    private val coinCosts = stats.costs[COIN_CURRENCY]?.toDouble() ?: 0.0
    private val profit = revenue - coinCosts
    private val period = ProfitTracker.displayPeriod(preset)
    private val renderItemIcons = config.details.showItemIcons
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val lines = buildLines()

    override val width: Int = maxOf(MINIMUM_WIDTH, lines.maxOfOrNull(ProfitLine::width) ?: 0) + padding * 2
    override val height: Int = lines.sumOf(ProfitLine::height) + padding * 2

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): LocalControlArea? {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = padding
        var hovered: LocalControlArea? = null
        lines.forEach { line ->
            renderLine(context, line, y, mouseX, mouseY)?.let { hovered = it }
            y += line.height
        }
        return hovered
    }

    private fun renderLine(
        context: GuiGraphicsExtractor,
        line: ProfitLine,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalControlArea? {
        val primaryWidth = line.primaryControlWidth(width, padding)
        val rightWidth = line.right?.let(LegacyTextRenderer::width) ?: 0
        val secondaryX = width - padding - rightWidth
        val primaryArea = line.control?.let { action ->
            LocalControlArea(action, padding, y, primaryWidth, line.height, emptyList())
        }
        val secondaryArea = line.secondaryControl?.let { action ->
            LocalControlArea(action, secondaryX, y, rightWidth, line.height, emptyList())
        }
        primaryArea?.takeIf { it.contains(mouseX, mouseY) }?.let { area ->
            context.fill(area.x, y, area.x + area.width, y + line.height, CONTROL_HOVER_COLOR)
        }
        secondaryArea?.takeIf { it.contains(mouseX, mouseY) }?.let { area ->
            context.fill(area.x, y, area.x + area.width, y + line.height, CONTROL_HOVER_COLOR)
        }
        line.leading?.let { LegacyTextRenderer.draw(context, it, padding, y + line.textYOffset) }
        line.icon?.let { ItemIconRenderable(it, ICON_SCALE).renderAt(context, padding + line.contentOffset, y) }
        val textX = if (line.centered) {
            (width - LegacyTextRenderer.width(line.left)) / 2
        } else {
            padding + line.contentOffset + if (line.icon == null) 0 else ITEM_TEXT_OFFSET
        }
        LegacyTextRenderer.draw(context, line.left, textX, y + line.textYOffset)
        line.middle?.let { middle ->
            LegacyTextRenderer.draw(context, middle, textX + line.leftColumnWidth + ITEM_COLUMN_GAP, y + line.textYOffset)
        }
        line.right?.let { right ->
            LegacyTextRenderer.draw(context, right, width - padding - LegacyTextRenderer.width(right), y + line.textYOffset)
        }
        val hoveredArea = secondaryArea?.takeIf { it.contains(mouseX, mouseY) }
            ?: primaryArea?.takeIf { it.contains(mouseX, mouseY) }
        return hoveredArea?.copy(tooltipLines = controlTooltip(hoveredArea.action))
    }

    private fun buildLines(): List<ProfitLine> = buildList {
        val itemRows = displayedItems.map { item -> item to item.name.truncateLegacyText(MAXIMUM_ITEM_NAME_LENGTH) }
        val itemNameColumnWidth = itemRows.maxOfOrNull { (_, name) -> LegacyTextRenderer.width(name) } ?: 0
        add(ProfitLine("§e§l${preset.displayName} Profit", height = TITLE_HEIGHT))
        if (displayedItems.isEmpty()) {
            add(ProfitLine("§7No tracked drops yet."))
        } else {
            itemRows.forEach { (item, name) ->
                val count = "§7x${item.amount.addSeparators()}"
                val value = item.value?.let { "§6${it.coinFormat()}" } ?: "§8Unknown"
                val quantityLeft = config.details.quantityPosition == ProfitTrackerQuantityPosition.LEFT
                add(
                    ProfitLine(
                        left = name,
                        middle = count.takeUnless { quantityLeft },
                        leftColumnWidth = if (quantityLeft) LegacyTextRenderer.width(name) else itemNameColumnWidth,
                        right = value,
                        icon = item.stack.takeIf { renderItemIcons },
                        height = ITEM_ROW_HEIGHT,
                        textYOffset = 2,
                        leading = count.takeIf { quantityLeft },
                        control = ProfitTrackerControl.ManageItem(item.itemId, item.stack, item.name)
                            .takeIf { inventoryOpen },
                    ),
                )
            }
        }
        if (remainingItems > 0) {
            add(ProfitLine("§7$remainingItems more...", centered = true))
        } else if (hiddenItemsAbove > 0) {
            add(ProfitLine("§7$hiddenItemsAbove above...", centered = true))
        }
        val profitLabel = when {
            stats.costs.keys.any { it != COIN_CURRENCY } -> "Coin Profit"
            hasUnknownPrices -> "Known Profit"
            else -> "Total Profit"
        }
        val profitPerHour = profitPerHour(profit, stats.activeMillis)
        config.details.summaryLines.get().distinct().forEach { summaryLine ->
            when (summaryLine) {
                ProfitTrackerSummaryLine.COINS -> if (stats.coins > 0.0) {
                    add(ProfitLine("§7${preset.coinLabel}", "§6${stats.coins.coinFormat()}"))
                }
                ProfitTrackerSummaryLine.QUEST_COSTS -> stats.costs.forEach { (currency, amount) ->
                    val value = if (currency == COIN_CURRENCY) amount.toDouble().coinFormat() else amount.addSeparators()
                    add(ProfitLine("§7Quest Costs", "§c-$value"))
                }
                ProfitTrackerSummaryLine.TOTAL_PROFIT -> {
                    add(ProfitLine("§7$profitLabel", profitColor(profit) + profit.signedCoinFormat()))
                }
                ProfitTrackerSummaryLine.PROFIT_PER_HOUR -> {
                    val label = if (profitLabel == "Total Profit") "Profit/h" else "$profitLabel/h"
                    add(ProfitLine("§7$label", profitColor(profitPerHour) + profitPerHour.signedCoinFormat()))
                }
                ProfitTrackerSummaryLine.ACTIONS -> {
                    add(ProfitLine("§7${preset.actionLabel}", "§e${stats.actions.addSeparators()}"))
                }
                ProfitTrackerSummaryLine.UPTIME -> {
                    val paused = if (ProfitTracker.isTimerPaused(preset)) " §c(paused)" else ""
                    add(ProfitLine("§7Uptime", "§b${formatProfitUptime(stats.activeMillis)}$paused"))
                }
            }
        }
        if (inventoryOpen) {
            add(ProfitLine("§7Display Mode §a§l[${period.displayName}]", control = ProfitTrackerControl.Period))
            add(ProfitLine("§7Price Source §e§l[${config.settings.priceSource}]", control = ProfitTrackerControl.PriceSource))
            add(
                ProfitLine(
                    "§c[Reset ${period.displayName}]",
                    right = "§7...",
                    control = ProfitTrackerControl.Reset,
                    secondaryControl = ProfitTrackerControl.More,
                ),
            )
        }
    }

    private fun controlTooltip(action: ProfitTrackerControl): List<String> = when (action) {
        ProfitTrackerControl.Period -> OverlayControlTooltips.cycle(
            "Display Mode",
            ProfitTrackingPeriod.entries.map(ProfitTrackingPeriod::displayName),
            period.ordinal,
        )
        ProfitTrackerControl.PriceSource -> OverlayControlTooltips.cycle(
            "Price Source",
            ProfitTrackerPriceSource.entries.map(ProfitTrackerPriceSource::toString),
            config.settings.priceSource.ordinal,
        )
        ProfitTrackerControl.Reset -> listOf("§7Reset ${period.displayName} ${preset.displayName} data.")
        ProfitTrackerControl.More -> listOf("§7Manage tracked items.")
        is ProfitTrackerControl.ManageItem -> emptyList()
        else -> emptyList()
    }
}

private data class ProfitLine(
    val left: String,
    val right: String? = null,
    val icon: ItemStack? = null,
    val height: Int = TEXT_ROW_HEIGHT,
    val textYOffset: Int = 0,
    val control: ProfitTrackerControl? = null,
    val secondaryControl: ProfitTrackerControl? = null,
    val centered: Boolean = false,
    val leading: String? = null,
    val middle: String? = null,
    val leftColumnWidth: Int = LegacyTextRenderer.width(left),
) {
    val leadingWidth: Int = leading?.let(LegacyTextRenderer::width) ?: 0
    val contentOffset: Int = leadingWidth + if (leading == null) 0 else ITEM_COLUMN_GAP
    val width: Int = contentOffset + (if (icon == null) 0 else ITEM_TEXT_OFFSET) + leftColumnWidth +
        (middle?.let { LegacyTextRenderer.width(it) + ITEM_COLUMN_GAP } ?: 0) +
        (right?.let { LegacyTextRenderer.width(it) + COLUMN_GAP } ?: 0)

    fun primaryControlWidth(totalWidth: Int, padding: Int): Int = when {
        control is ProfitTrackerControl.ManageItem -> totalWidth - padding * 2
        secondaryControl == null -> width
        else -> LegacyTextRenderer.width(left)
    }
}

internal fun formatProfitUptime(activeMillis: Long): String {
    val totalSeconds = (activeMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND_LONG)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return buildList {
        if (hours > 0L) add("${hours}h")
        if (minutes > 0L || hours > 0L) add("${minutes}m")
        add("${seconds}s")
    }.joinToString(" ")
}

internal fun profitPerHour(profit: Double, activeMillis: Long): Double =
    if (activeMillis > 0L) profit * MILLIS_PER_HOUR / activeMillis else 0.0

internal fun profitTrackerScrollOffset(current: Int, verticalAmount: Double, maximumOffset: Int): Int =
    (current + if (verticalAmount < 0.0) 1 else -1).coerceIn(0, maximumOffset)

internal fun nextProfitTrackerPriceSource(
    current: ProfitTrackerPriceSource,
    backwards: Boolean,
): ProfitTrackerPriceSource {
    val step = if (backwards) -1 else 1
    return ProfitTrackerPriceSource.entries[
        Math.floorMod(current.ordinal + step, ProfitTrackerPriceSource.entries.size)
    ]
}

private data class ItemScrollKey(
    val preset: ProfitTrackerPreset,
    val period: ProfitTrackingPeriod,
)

private data class ProfitDisplayItem(
    val itemId: String,
    val name: String,
    val stack: ItemStack,
    val amount: Long,
    val value: Double?,
)

private data class LocalControlArea(
    val action: ProfitTrackerControl,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val tooltipLines: List<String>,
)

private fun LocalControlArea.contains(mouseX: Int?, mouseY: Int?): Boolean =
    mouseX != null && mouseY != null && mouseX in x..(x + width) && mouseY in y..(y + height)

private fun profitColor(value: Double): String = if (value >= 0.0) "§a" else "§c"

private const val COIN_CURRENCY = "Coins"
private const val MILLIS_PER_HOUR = 3_600_000.0
private const val MILLIS_PER_SECOND_LONG = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val MAXIMUM_ITEMS = 15
private const val MAXIMUM_ITEM_NAME_LENGTH = 20
private const val MINIMUM_WIDTH = 145
private const val TITLE_HEIGHT = 12
private const val TEXT_ROW_HEIGHT = 11
private const val ITEM_ROW_HEIGHT = 13
private const val ICON_SCALE = 0.75
private const val ITEM_TEXT_OFFSET = 14
private const val COLUMN_GAP = 8
private const val ITEM_COLUMN_GAP = 4
private const val SIDE_PANEL_ESTIMATED_WIDTH = 220
private const val CONTROL_HOVER_COLOR = 0x20FFFFFF
