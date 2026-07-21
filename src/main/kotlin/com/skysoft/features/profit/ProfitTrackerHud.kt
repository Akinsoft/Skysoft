package com.skysoft.features.profit

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
import com.skysoft.utils.SoundUtilities
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
import org.lwjgl.glfw.GLFW

private val config get() = SkysoftConfigGui.config().profitTracker
private var hoveredControl: OverlayControlArea<ProfitTrackerControl>? = null
private var isTrackerHovered = false
private val itemScrollOffsets = mutableMapOf<ItemScrollKey, Int>()

internal fun registerProfitTrackerHud() {
    registerMouseCapture()
    GuiOverlayRegistry.register(
        GuiOverlay(
            id = "profit_tracker",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = GuiOverlayContextType.entries.toSet(),
            render = { context, _ -> renderProfitTracker(context) },
        ),
    )
    HudEditorRegistry.register(object : HudEditorElement {
        override val id: String = "profit_tracker"
        override val label: String = "Profit Tracker"
        override val position get() = config.position
        override val hasEditorBackground: Boolean get() = !config.details.showBackground
        override fun width(): Int = buildProfitRenderable(false)?.width ?: 0
        override fun height(): Int = buildProfitRenderable(false)?.height ?: 0
        override fun isVisible(): Boolean = config.enabled
        override fun renderDummy(context: GuiGraphicsExtractor) {
            buildProfitRenderable(false)?.render(context)
        }
        override fun openConfig() = SkysoftConfigGui.open("Profit Tracker")
    })
}

private fun renderProfitTracker(context: GuiGraphicsExtractor) {
    val minecraft = Minecraft.getInstance()
    if (!config.enabled || !HypixelLocationState.inSkyBlock || MinecraftClient.isGuiHidden(minecraft)) {
        hoveredControl = null
        isTrackerHovered = false
        return
    }
    val inventoryOpen = MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
    val renderable = buildProfitRenderable(inventoryOpen) ?: run {
        hoveredControl = null
        isTrackerHovered = false
        return
    }
    val window = minecraft.window
    val mouseX = minecraft.mouseHandler.getScaledXPos(window).toInt()
    val mouseY = minecraft.mouseHandler.getScaledYPos(window).toInt()
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    context.nextStratum()
    renderPositioned(context, renderable, inventoryOpen, normalMouseX, normalMouseY)
    if (inventoryOpen) {
        context.nextStratum()
        hoveredControl?.let { area ->
            val (tooltipX, tooltipY) = OverlayControlMouse.deferredTooltipPoint(mouseX, mouseY)
            SkysoftNativeTooltip.setForNextFrame(context, area.tooltipLines, tooltipX, tooltipY)
        }
    }
}

private fun renderPositioned(
    context: GuiGraphicsExtractor,
    renderable: ProfitTrackerRenderable,
    interactive: Boolean,
    mouseX: Int,
    mouseY: Int,
) {
    val scale = config.position.effectiveScale
    val scaledWidth = (renderable.width * scale).roundToInt()
    val scaledHeight = (renderable.height * scale).roundToInt()
    val x = config.position.getAbsX0AllowingOverflow(scaledWidth)
    val y = config.position.getAbsY0AllowingOverflow(scaledHeight)
    val localMouseX = floor((mouseX - x) / scale).toInt()
    val localMouseY = floor((mouseY - y) / scale).toInt()
    val localControl = context.withIsolatedPose {
        pose().translate(x.toFloat(), y.toFloat())
        pose().scale(scale, scale)
        renderable.renderInteractive(context, if (interactive) localMouseX else null, if (interactive) localMouseY else null)
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

private fun buildProfitRenderable(inventoryOpen: Boolean): ProfitTrackerRenderable? {
    val preset = ProfitTracker.selectedPreset()?.takeIf(ProfitTracker::isInPresetArea) ?: return null
    val stats = ProfitTracker.stats(preset)
    val items = profitDisplayItems(stats)
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
        background = config.details.showBackground,
    )
}

private fun registerMouseCapture() {
    ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
        if (!config.enabled || screen !is AbstractContainerScreen<*>) return@register
        ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
            SkysoftErrorBoundary.value("Profit Tracker mouse click", true) {
                !config.enabled || !wasControlClickHandled(click.button())
            }
        }
        ScreenMouseEvents.allowMouseScroll(screen).register { _, _, _, _, verticalAmount ->
            SkysoftErrorBoundary.value("Profit Tracker mouse scroll", true) {
                !config.enabled || !isTrackerHovered || !wasItemScrollHandled(verticalAmount)
            }
        }
    }
}

private fun wasControlClickHandled(button: Int): Boolean {
    val area = hoveredControl ?: return false
    val preset = ProfitTracker.selectedPreset() ?: return false
    val activated = when (area.action) {
        ProfitTrackerControl.PERIOD -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
            ProfitTracker.cyclePeriod(preset, backwards = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            true
        }
        ProfitTrackerControl.RESET -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
            ProfitTracker.selectedPreset()?.let(ProfitTracker::resetDisplayed)
            true
        }
    }
    if (activated) SoundUtilities.playClickSound()
    return activated
}

private fun wasItemScrollHandled(verticalAmount: Double): Boolean {
    if (verticalAmount == 0.0) return false
    val preset = ProfitTracker.selectedPreset() ?: return false
    val period = ProfitTracker.displayPeriod(preset)
    val maximumItems = config.settings.maximumItems.coerceIn(1, MAXIMUM_ITEMS)
    val maximumOffset = (profitDisplayItems(ProfitTracker.stats(preset)).size - maximumItems).coerceAtLeast(0)
    if (maximumOffset == 0) return false
    val key = ItemScrollKey(preset, period)
    val current = itemScrollOffsets.getOrDefault(key, 0)
    itemScrollOffsets[key] = profitTrackerScrollOffset(current, verticalAmount, maximumOffset)
    return true
}

private fun profitDisplayItems(stats: ProfileStorage.ProfitTrackerStats): List<ProfitDisplayItem> =
    stats.itemCounts.mapNotNull { (itemId, amount) ->
        val key = SkyBlockDataRepository.itemKey(itemId)
        val stack = SkyBlockDataRepository.displayStack(key) ?: PetRepository.itemStackOrNull(itemId) ?: return@mapNotNull null
        val name = (SkyBlockDataRepository.entry(key)?.formattedDisplayName ?: PetRepository.itemName(itemId) ?: itemId)
            .replace("Enchanted ", "Ench ")
        val unitValue = ProfitTracker.unitValue(itemId)
        ProfitDisplayItem(name, stack, amount, unitValue?.times(amount))
    }.sortedWith(compareByDescending<ProfitDisplayItem> { it.value ?: Double.NEGATIVE_INFINITY }.thenBy { it.name })

private class ProfitTrackerRenderable(
    private val preset: ProfitTrackerPreset,
    private val stats: ProfileStorage.ProfitTrackerStats,
    items: List<ProfitDisplayItem>,
    maximumItems: Int,
    scrollOffset: Int,
    private val inventoryOpen: Boolean,
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
            line.leading?.let { LegacyTextRenderer.draw(context, it, padding, y + line.textYOffset) }
            line.icon?.let { ItemIconRenderable(it, ICON_SCALE).renderAt(context, padding + line.contentOffset, y) }
            val textX = if (line.centered) {
                (width - LegacyTextRenderer.width(line.left)) / 2
            } else {
                padding + line.contentOffset + if (line.icon == null) 0 else ITEM_TEXT_OFFSET
            }
            LegacyTextRenderer.draw(context, line.left, textX, y + line.textYOffset)
            line.middle?.let { middle ->
                val middleX = textX + line.leftColumnWidth + ITEM_COLUMN_GAP
                LegacyTextRenderer.draw(context, middle, middleX, y + line.textYOffset)
            }
            line.right?.let { right ->
                LegacyTextRenderer.draw(context, right, width - padding - LegacyTextRenderer.width(right), y + line.textYOffset)
            }
            line.control?.let { action ->
                val area = LocalControlArea(
                    action = action,
                    x = padding,
                    y = y,
                    width = line.width,
                    height = line.height,
                    tooltipLines = controlTooltip(action),
                )
                if (mouseX != null && mouseY != null && mouseX in area.x..(area.x + area.width) &&
                    mouseY in area.y..(area.y + area.height)
                ) {
                    hovered = area
                }
            }
            y += line.height
        }
        return hovered
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
                        icon = item.stack.takeIf { config.details.showItemIcons },
                        height = ITEM_ROW_HEIGHT,
                        textYOffset = 2,
                        leading = count.takeIf { quantityLeft },
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
            add(ProfitLine("§7Display Mode §a§l[${period.displayName}]", control = ProfitTrackerControl.PERIOD))
            add(ProfitLine("§c[Reset ${period.displayName}]", control = ProfitTrackerControl.RESET))
        }
    }

    private fun controlTooltip(action: ProfitTrackerControl): List<String> = when (action) {
        ProfitTrackerControl.PERIOD -> OverlayControlTooltips.cycle(
            "Display Mode",
            ProfitTrackingPeriod.entries.map(ProfitTrackingPeriod::displayName),
            period.ordinal,
        )
        ProfitTrackerControl.RESET -> listOf("§7Reset ${period.displayName} ${preset.displayName} data.")
    }
}

private data class ProfitLine(
    val left: String,
    val right: String? = null,
    val icon: ItemStack? = null,
    val height: Int = TEXT_ROW_HEIGHT,
    val textYOffset: Int = 0,
    val control: ProfitTrackerControl? = null,
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

private data class ItemScrollKey(
    val preset: ProfitTrackerPreset,
    val period: ProfitTrackingPeriod,
)

private data class ProfitDisplayItem(
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

private enum class ProfitTrackerControl {
    PERIOD,
    RESET,
}

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
