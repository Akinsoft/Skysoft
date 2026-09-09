package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerConfig
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.ProfitTrackerQuantityPosition
import com.skysoft.config.ProfitTrackerSummaryLine
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.slayer.SlayerTimeToKill
import com.skysoft.features.slayer.formatSlayerKillTimeForHud
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.DurationParts
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.NumberUtilities.roundTo
import com.skysoft.utils.NumberUtilities.signedCoinFormat
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

internal class ProfitTrackerRenderable(
    private val target: ProfitTrackerTarget,
    private val stats: ProfileStorageView.ProfitTrackerStats,
    items: List<ProfitDisplayItem>,
    maximumItems: Int,
    scrollOffset: Int,
    private val inventoryOpen: Boolean,
    private val config: ProfitTrackerConfig,
    private val background: Boolean,
    private val hudControls: ProfitTrackerHudControls,
) : GuiRenderable {
    private val displayedItems = items.drop(scrollOffset).take(maximumItems)
    private val remainingItems = (items.size - scrollOffset - displayedItems.size).coerceAtLeast(0)
    private val hiddenItemsAbove = scrollOffset
    private val kernelProfit = if (target.preset == ProfitTrackerPreset.FARMING) {
        farmingKernelProfit(
            stats.kernels,
            farmingKernelProfitItem,
            farmingKernelProfitPriceSource.source ?: config.settings.priceSource,
        )
    } else {
        0.0
    }
    private val revenue = items.sumOf { it.value ?: 0.0 } + stats.coins + (kernelProfit ?: 0.0)
    private val hasUnknownPrices = items.any { it.value == null } || kernelProfit == null
    private val profitLabel = when {
        stats.costs.keys.any { it != COIN_CURRENCY } -> "Coin Profit"
        hasUnknownPrices -> "Known Profit"
        else -> "Total Profit"
    }
    private val coinCosts = stats.costs[COIN_CURRENCY]?.toDouble() ?: 0.0
    private val profit = revenue - coinCosts
    private val period = ProfitTracker.displayPeriod(target)
    private val killTimeDisplay = target.slayerType?.let { SlayerTimeToKill.displayStats(it, period) }
    private val summaryLines = config.details.summaryLines.get().distinct().filter { summaryLine ->
        killTimeDisplay != null || !summaryLine.requiresKillTime
    }
    private val renderItemIcons = config.details.showItemIcons
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val resetLine = ProfitLine(
        "§c[Reset ${period.displayName}]",
        right = "§7...",
        control = ProfitTrackerControl.Reset,
        secondaryControl = ProfitTrackerControl.More,
    )
    private val resetConfirmationLine = ProfitLine(
        "§c[Cancel]",
        right = "§a[Confirm]",
        control = ProfitTrackerControl.CancelReset,
        secondaryControl = ProfitTrackerControl.ConfirmReset,
    )
    private val pestBreakdownControl = if (inventoryOpen && target.preset == ProfitTrackerPreset.FARMING) {
        ProfitTrackerControl.PestBreakdown(pestBreakdownRows())
    } else {
        null
    }
    private val kernelProfitLine = if (
        target.preset == ProfitTrackerPreset.FARMING && MayorPerkApi.grandFeastActive
    ) {
        ProfitLine(
            "§7Kernel Profit",
            kernelProfit?.let { "§6${it.coinFormat()}" } ?: "§8Unknown",
            control = ProfitTrackerControl.ManageKernels.takeIf { inventoryOpen },
        )
    } else {
        null
    }
    private val lines = buildLines()

    override val width: Int = maxOf(
        MINIMUM_WIDTH,
        lines.maxOfOrNull(ProfitLine::width) ?: 0,
        resetLine.width.takeIf { inventoryOpen } ?: 0,
        resetConfirmationLine.width.takeIf { inventoryOpen } ?: 0,
    ) + padding * 2
    override val height: Int = lines.sumOf(ProfitLine::height) +
        (if (inventoryOpen) resetLine.height else 0) + padding * 2

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): OverlayControlArea<ProfitTrackerControl>? {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = padding
        var hovered: OverlayControlArea<ProfitTrackerControl>? = null
        lines.forEach { line ->
            renderLine(context, line, y, mouseX, mouseY)?.let { hovered = it }
            y += line.height
        }
        if (inventoryOpen) renderResetLine(context, y, mouseX, mouseY)?.let { hovered = it }
        return hovered
    }

    private fun renderResetLine(
        context: GuiGraphicsExtractor,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<ProfitTrackerControl>? {
        val confirmationOpacity = hudControls.resetConfirmationOpacity(target, period)
        val confirmationPending = hudControls.isResetConfirmationPending(target, period)
        val confirmationInteractive = hudControls.isResetConfirmationInteractive(target, period)
        val resetArea = renderLine(
            context,
            resetLine,
            y,
            mouseX.takeUnless { confirmationPending },
            mouseY.takeUnless { confirmationPending },
            1.0 - confirmationOpacity,
        )
        val confirmationArea = renderLine(
            context,
            resetConfirmationLine,
            y,
            mouseX.takeIf { confirmationInteractive },
            mouseY.takeIf { confirmationInteractive },
            confirmationOpacity,
        )
        return confirmationArea ?: resetArea
    }

    private fun renderLine(
        context: GuiGraphicsExtractor,
        line: ProfitLine,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
        opacity: Double = 1.0,
    ): OverlayControlArea<ProfitTrackerControl>? {
        val primaryWidth = line.primaryControlWidth(width, padding)
        val rightWidth = line.right?.let(LegacyTextRenderer::width) ?: 0
        val secondaryX = width - padding - rightWidth
        val primaryArea = line.control?.let { action ->
            OverlayControlArea(action, Rect(padding, y, primaryWidth, line.height), emptyList())
        }
        val secondaryArea = line.secondaryControl?.let { action ->
            OverlayControlArea(action, Rect(secondaryX, y, rightWidth, line.height), emptyList())
        }
        primaryArea?.takeIf { it.containsPointer(mouseX, mouseY) }?.let { area ->
            OverlayTextStyle.drawControlHover(context, area.bounds, opacity)
        }
        secondaryArea?.takeIf { it.containsPointer(mouseX, mouseY) }?.let { area ->
            OverlayTextStyle.drawControlHover(context, area.bounds, opacity)
        }
        val textColor = TEXT_COLOR.withScaledAlpha(opacity)
        line.leading?.let {
            LegacyTextRenderer.draw(context, it, padding, y + line.textYOffset, defaultColor = textColor)
        }
        line.icon?.let {
            ItemIconRenderable(it, OverlayItemRowStyle.ICON_SCALE).renderAt(context, padding + line.contentOffset, y)
        }
        val textX = if (line.centered) {
            (width - LegacyTextRenderer.width(line.left)) / 2
        } else {
            padding + line.contentOffset + if (line.icon == null) 0 else OverlayItemRowStyle.ICON_TEXT_OFFSET
        }
        LegacyTextRenderer.draw(context, line.left, textX, y + line.textYOffset, defaultColor = textColor)
        line.middle?.let { middle ->
            LegacyTextRenderer.draw(
                context,
                middle,
                textX + line.leftColumnWidth + OverlayItemRowStyle.QUANTITY_COLUMN_GAP,
                y + line.textYOffset,
                defaultColor = textColor,
            )
        }
        line.right?.let { right ->
            LegacyTextRenderer.draw(
                context,
                right,
                width - padding - LegacyTextRenderer.width(right),
                y + line.textYOffset,
                defaultColor = textColor,
            )
        }
        val hoveredArea = secondaryArea?.takeIf { it.containsPointer(mouseX, mouseY) }
            ?: primaryArea?.takeIf { it.containsPointer(mouseX, mouseY) }
        return hoveredArea?.copy(tooltipLines = controlTooltip(hoveredArea.action))
    }

    private fun buildLines(): List<ProfitLine> = buildList {
        val itemRows = displayedItems.map { item -> item to item.name.truncateLegacyText(MAXIMUM_ITEM_NAME_LENGTH) }
        val itemNameColumnWidth = itemRows.maxOfOrNull { (_, name) -> LegacyTextRenderer.width(name) } ?: 0
        add(ProfitLine(OverlayTextStyle.title("${target.displayName} Profit"), height = OverlayTextStyle.TITLE_HEIGHT))
        if (displayedItems.isEmpty()) {
            add(ProfitLine("§7No tracked drops yet."))
        } else {
            itemRows.forEach { (item, name) ->
                val count = itemQuantity(item)
                val countWidth = LegacyTextRenderer.width("§7x§a§l${item.amount.addSeparators()}")
                val value = item.value?.let { "§6${it.coinFormat()}" } ?: "§8Unknown"
                val quantityLeft = config.details.quantityPosition == ProfitTrackerQuantityPosition.LEFT
                add(
                    ProfitLine(
                        left = name,
                        middle = count.takeUnless { quantityLeft },
                        leftColumnWidth = if (quantityLeft) LegacyTextRenderer.width(name) else itemNameColumnWidth,
                        right = value,
                        icon = item.stack.takeIf { renderItemIcons },
                        height = OverlayItemRowStyle.HEIGHT,
                        textYOffset = OverlayItemRowStyle.TEXT_Y_OFFSET,
                        leading = count.takeIf { quantityLeft },
                        reservedColumnWidth = countWidth,
                        control = ProfitTrackerControl.ManageItem(item.itemId, item.stack, item.name)
                            .takeIf { inventoryOpen },
                    ),
                )
            }
        }
        val scrollIndicator = OverlayListScroll.indicator(hiddenItemsAbove, remainingItems)
        if (scrollIndicator.isNotEmpty()) add(ProfitLine(scrollIndicator, centered = true))
        val profitPerHour = profitPerHour(profit, stats.activeMillis)
        summaryLines.forEach { summaryLine ->
            when (summaryLine) {
                ProfitTrackerSummaryLine.COINS -> if (stats.coins > 0.0) {
                    add(ProfitLine("§7${target.coinLabel}", "§6${stats.coins.coinFormat()}"))
                }
                ProfitTrackerSummaryLine.KERNEL_PROFIT -> kernelProfitLine?.let(::add)
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
                    add(
                        ProfitLine(
                            "§7${target.actionLabel}",
                            "§e${stats.actions.addSeparators()}",
                            control = pestBreakdownControl,
                        ),
                    )
                }
                ProfitTrackerSummaryLine.AVERAGE_KILL_TIME -> add(
                    ProfitLine(
                        "§7Average Kill",
                        formatSlayerKillTimeForHud(requireNotNull(killTimeDisplay).averageMillis),
                    ),
                )
                ProfitTrackerSummaryLine.PERSONAL_BEST -> add(
                    ProfitLine(
                        "§7Personal Best",
                        formatSlayerKillTimeForHud(requireNotNull(killTimeDisplay).personalBestMillis),
                    ),
                )
                ProfitTrackerSummaryLine.UPTIME -> {
                    val paused = if (ProfitTracker.isTimerPaused(target)) " §c(paused)" else ""
                    add(ProfitLine("§7Uptime", "§b${formatProfitUptime(stats.activeMillis)}$paused"))
                }
            }
        }
        if (inventoryOpen) {
            add(ProfitLine("§7Display Mode §a§l[${period.displayName}]", control = ProfitTrackerControl.Period))
            add(ProfitLine("§7Price Source §e§l[${config.settings.priceSource}]", control = ProfitTrackerControl.PriceSource))
        }
    }

    private fun itemQuantity(item: ProfitDisplayItem): String {
        val highlighted = config.details.highlightChanges &&
            ProfitTracker.itemQuantityHighlights.isHighlighted(target.storageKey to item.itemId)
        val style = if (highlighted) "§a§l" else ""
        return "§7x$style${item.amount.addSeparators()}"
    }

    private fun pestBreakdownRows(): List<SkysoftNativeTooltip.ItemRow> {
        if (stats.actions == 0L) return emptyList()
        val counts = stats.pestKills.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
            .toMutableList()
        val unrecorded = stats.actions - stats.pestKills.values.sum()
        if (unrecorded > 0L) counts.add("Not recorded" to unrecorded)
        val pests = SkyBlockDataRepository.entries.asSequence()
            .filter { it.key.kind == ItemListEntryKind.ENTITY && "Pest" in it.tags }
            .associateBy { it.displayName }
        return counts.map { (pest, count) ->
            val percentage = (count * PERCENT_SCALE / stats.actions).roundTo(1).toString().removeSuffix(".0")
            SkysoftNativeTooltip.ItemRow(
                stack = pests[pest]?.let { SkyBlockDataRepository.displayStack(it.key) },
                label = "§7$pest §e${count.addSeparators()}",
                value = "§7($percentage%)",
            )
        }
    }

    private fun controlTooltip(action: ProfitTrackerControl): List<String> = when (action) {
        ProfitTrackerControl.Period -> OverlayControlTooltips.cycle(
            "Display Mode",
            target.trackingPeriods.map(ProfitTrackingPeriod::displayName),
            target.trackingPeriods.indexOf(period),
        )
        ProfitTrackerControl.PriceSource -> OverlayControlTooltips.cycle(
            "Price Source",
            ProfitTrackerPriceSource.entries.map(ProfitTrackerPriceSource::toString),
            config.settings.priceSource.ordinal,
        )
        ProfitTrackerControl.Reset,
        ProfitTrackerControl.ConfirmReset,
        -> listOf("§7Reset ${period.displayName} ${target.displayName} data.")
        ProfitTrackerControl.CancelReset -> emptyList()
        ProfitTrackerControl.More -> listOf("§7Manage tracked items.")
        ProfitTrackerControl.ManageKernels -> listOf("§7Manage Kernel profit.")
        is ProfitTrackerControl.PestBreakdown -> listOf("§ePests Vacuumed", "§7No pests vacuumed yet.")
        is ProfitTrackerControl.ManageItem -> emptyList()
        else -> emptyList()
    }
}

private data class ProfitLine(
    val left: String,
    val right: String? = null,
    val icon: ItemStack? = null,
    val height: Int = OverlayTextStyle.ROW_HEIGHT,
    val textYOffset: Int = 0,
    val control: ProfitTrackerControl? = null,
    val secondaryControl: ProfitTrackerControl? = null,
    val centered: Boolean = false,
    val leading: String? = null,
    val middle: String? = null,
    val reservedColumnWidth: Int? = null,
    val leftColumnWidth: Int = LegacyTextRenderer.width(left),
) {
    val leadingWidth: Int = leading?.let { reservedColumnWidth ?: LegacyTextRenderer.width(it) } ?: 0
    private val middleWidth: Int = middle?.let { reservedColumnWidth ?: LegacyTextRenderer.width(it) } ?: 0
    val contentOffset: Int = leadingWidth + if (leading == null) 0 else OverlayItemRowStyle.QUANTITY_COLUMN_GAP
    val width: Int = contentOffset + (if (icon == null) 0 else OverlayItemRowStyle.ICON_TEXT_OFFSET) +
        leftColumnWidth + (middle?.let { middleWidth + OverlayItemRowStyle.QUANTITY_COLUMN_GAP } ?: 0) +
        (right?.let { LegacyTextRenderer.width(it) + OverlayItemRowStyle.VALUE_COLUMN_GAP } ?: 0)

    fun primaryControlWidth(totalWidth: Int, padding: Int): Int = when {
        control is ProfitTrackerControl.ManageItem || control is ProfitTrackerControl.PestBreakdown ||
            control == ProfitTrackerControl.ManageKernels ->
            totalWidth - padding * 2
        secondaryControl == null -> width
        else -> LegacyTextRenderer.width(left)
    }
}

internal fun formatProfitUptime(activeMillis: Long): String {
    val duration = DurationParts.fromMilliseconds(activeMillis)
    return buildList {
        if (duration.totalHours > 0L) add("${duration.totalHours}h")
        if (duration.minutes > 0L || duration.totalHours > 0L) add("${duration.minutes}m")
        add("${duration.seconds}s")
    }.joinToString(" ")
}

internal fun profitPerHour(profit: Double, activeMillis: Long): Double =
    if (activeMillis > 0L) profit * MILLIS_PER_HOUR / activeMillis else 0.0

internal data class ProfitDisplayItem(
    val itemId: String,
    val name: String,
    val stack: ItemStack,
    val amount: Long,
    val value: Double?,
)

private fun OverlayControlArea<ProfitTrackerControl>.containsPointer(mouseX: Int?, mouseY: Int?): Boolean =
    mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)

private fun profitColor(value: Double): String = if (value >= 0.0) "§a" else "§c"

private const val COIN_CURRENCY = "Coins"
private const val MILLIS_PER_HOUR = 3_600_000.0
private const val PERCENT_SCALE = 100.0
private const val MAXIMUM_ITEM_NAME_LENGTH = 20
private const val MINIMUM_WIDTH = 145
private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
